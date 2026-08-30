package com.mealflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.LocalEventStatus;
import com.mealflow.common.status.OrderStatus;
import com.mealflow.infra.event.EventKey;
import com.mealflow.order.client.CatalogClient;
import com.mealflow.order.client.PaymentClient;
import com.mealflow.order.client.PromotionClient;
import com.mealflow.order.client.QueueClient;
import com.mealflow.order.mapper.LocalEventMapper;
import com.mealflow.order.mapper.OrderMapper;
import com.mealflow.order.mapper.OrderRow;
import com.mealflow.order.mapper.OrderSagaMapper;
import com.mealflow.order.mapper.OrderSagaStepRow;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderSagaCoordinator {
  static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
  static final String CANCEL_PAID = "CANCEL_PAID";
  static final String CANCEL_UNPAID = "CANCEL_UNPAID";
  static final String ORPHAN_PAYMENT = "ORPHAN_PAYMENT";
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() { };

  private final OrderSagaMapper sagaMapper;
  private final OrderMapper orderMapper;
  private final LocalEventMapper localEventMapper;
  private final CatalogClient catalogClient;
  private final PromotionClient promotionClient;
  private final QueueClient queueClient;
  private final PaymentClient paymentClient;
  private final DatabaseIdGenerator idGenerator;
  private final ObjectMapper objectMapper;
  private final OrderService orderService;
  private final TransactionTemplate transactionTemplate;

  public OrderSagaCoordinator(OrderSagaMapper sagaMapper, OrderMapper orderMapper, LocalEventMapper localEventMapper,
      CatalogClient catalogClient, PromotionClient promotionClient, QueueClient queueClient,
      PaymentClient paymentClient, DatabaseIdGenerator idGenerator, ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager, @Lazy OrderService orderService) {
    this.sagaMapper = sagaMapper;
    this.orderMapper = orderMapper;
    this.localEventMapper = localEventMapper;
    this.catalogClient = catalogClient;
    this.promotionClient = promotionClient;
    this.queueClient = queueClient;
    this.paymentClient = paymentClient;
    this.idGenerator = idGenerator;
    this.objectMapper = objectMapper;
    this.orderService = orderService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void enqueuePaymentSuccess(OrderRow order) {
    if (OrderStatus.valueOf(order.getStatus()) != OrderStatus.PENDING_PAYMENT) {
      return;
    }
    insertSteps(order, PAYMENT_SUCCESS, null, "CONFIRM_STOCK", "CONFIRM_VOUCHER", "ADVANCE_ORDER");
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void enqueueCancellation(OrderRow order, String reason) {
    OrderStatus status = OrderStatus.valueOf(order.getStatus());
    if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.WAIT_MERCHANT_ACCEPT) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be cancelled");
    }
    if (sagaMapper.countIncomplete(order.getId(), PAYMENT_SUCCESS) > 0) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "payment confirmation is still in progress");
    }
    if (status == OrderStatus.PENDING_PAYMENT) {
      insertSteps(order, CANCEL_UNPAID, reason, "CLOSE_PAYMENT", "RELEASE_STOCK", "RELEASE_VOUCHER",
          "RELEASE_CAPACITY", "CREATE_PROMOTED_QUEUE_ORDER", "CANCEL_ORDER");
    } else {
      insertSteps(order, CANCEL_PAID, reason, "REFUND_PAYMENT", "REVERT_STOCK", "REVERT_VOUCHER",
          "RELEASE_CAPACITY", "CREATE_PROMOTED_QUEUE_ORDER", "CANCEL_ORDER");
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void enqueueOrphanPayment(long orderId, long payOrderId) {
    insertStep(orderId, payOrderId, ORPHAN_PAYMENT, "CLOSE_PAYMENT", 1, "ORDER_CREATE_FAILED");
  }

  public int processReadyForOrder(long orderId) {
    int completed = 0;
    for (int i = 0; i < 8; i++) {
      OrderSagaStepRow step = sagaMapper.findReadyForOrder(orderId, LocalDateTime.now());
      if (step == null || !process(step)) {
        break;
      }
      completed++;
    }
    return completed;
  }

  public boolean hasIncompleteCancellation(long orderId) {
    return sagaMapper.countIncomplete(orderId, CANCEL_PAID) > 0
        || sagaMapper.countIncomplete(orderId, CANCEL_UNPAID) > 0;
  }

  public int dispatchReady(int limit) {
    sagaMapper.recoverExpired(LocalDateTime.now());
    int completed = 0;
    while (completed < limit) {
      List<OrderSagaStepRow> ready = sagaMapper.findReady(LocalDateTime.now(), Math.min(20, limit - completed));
      if (ready.isEmpty()) {
        break;
      }
      int progressed = 0;
      for (OrderSagaStepRow step : ready) {
        if (process(step)) {
          completed++;
          progressed++;
        }
      }
      if (progressed == 0) {
        break;
      }
    }
    return completed;
  }

  private boolean process(OrderSagaStepRow step) {
    LocalDateTime now = LocalDateTime.now();
    Boolean claimed = transactionTemplate.execute(status ->
        sagaMapper.markProcessing(step.getId(), now, now.plusMinutes(1)) == 1);
    if (!Boolean.TRUE.equals(claimed)) {
      return false;
    }
    try {
      execute(step);
      transactionTemplate.executeWithoutResult(status -> sagaMapper.markSuccess(step.getId(), LocalDateTime.now()));
      return true;
    } catch (RuntimeException ex) {
      LocalDateTime failedAt = LocalDateTime.now();
      transactionTemplate.executeWithoutResult(status -> sagaMapper.markFailed(step.getId(), trimError(ex),
          retryAt(step.getRetryCount() + 1, failedAt), failedAt));
      return false;
    }
  }

  private void execute(OrderSagaStepRow step) {
    if (ORPHAN_PAYMENT.equals(step.getSagaType())) {
      paymentClient.close(step.getPayOrderId(), new PaymentClient.ClosePaymentRequest(
          "payment-close-orphan:" + step.getOrderId(), step.getReason()));
      return;
    }
    OrderRow order = requireOrder(step.getOrderId());
    switch (step.getStepName()) {
      case "CONFIRM_STOCK" -> catalogClient.confirm(new CatalogClient.StockTransitionRequest(
          "stock-confirm:" + order.getId(), reservationIds(order), order.getId(), "PAYMENT_SUCCESS"));
      case "CONFIRM_VOUCHER" -> promotionClient.confirm(new PromotionClient.VoucherTransitionRequest(
          "voucher-confirm:" + order.getId(), order.getVoucherLockId(), order.getId(), "PAYMENT_SUCCESS"));
      case "CLOSE_PAYMENT" -> paymentClient.close(order.getPayOrderId(), new PaymentClient.ClosePaymentRequest(
          "payment-close:" + order.getId(), step.getReason()));
      case "REFUND_PAYMENT" -> paymentClient.refund(order.getPayOrderId());
      case "RELEASE_STOCK" -> catalogClient.release(new CatalogClient.StockTransitionRequest(
          "stock-release:" + order.getId(), reservationIds(order), order.getId(), step.getReason()));
      case "RELEASE_VOUCHER" -> promotionClient.release(new PromotionClient.VoucherTransitionRequest(
          "voucher-release:" + order.getId(), order.getVoucherLockId(), order.getId(), step.getReason()));
      case "REVERT_STOCK" -> catalogClient.revertConfirmed(new CatalogClient.StockTransitionRequest(
          "stock-revert:" + order.getId(), reservationIds(order), order.getId(), step.getReason()));
      case "REVERT_VOUCHER" -> promotionClient.revertConfirmed(new PromotionClient.VoucherTransitionRequest(
          "voucher-revert:" + order.getId(), order.getVoucherLockId(), order.getId(), step.getReason()));
      case "RELEASE_CAPACITY" -> persistPromotionResult(step, queueClient.release(order.getCapacityTokenId(),
          new QueueClient.ReleaseCapacityRequest("capacity-release:" + order.getId(), "ORDER_CANCELLED")));
      case "CREATE_PROMOTED_QUEUE_ORDER" -> createPromotedOrder(step);
      case "ADVANCE_ORDER" -> completeOrder(order, OrderStatus.PENDING_PAYMENT,
          OrderStatus.WAIT_MERCHANT_ACCEPT, "OrderPaid");
      case "CANCEL_ORDER" -> completeCancellation(order);
      default -> throw new IllegalStateException("unsupported order saga step: " + step.getStepName());
    }
  }

  private void persistPromotionResult(OrderSagaStepRow step, QueueClient.ReleaseCapacityResponse release) {
    QueueClient.QueueReadyTicket ticket = release.readyTicket();
    transactionTemplate.executeWithoutResult(status -> sagaMapper.savePromotionResult(step.getId(),
        ticket == null ? null : ticket.ticketId(), ticket == null ? null : ticket.capacityTokenId(),
        LocalDateTime.now()));
  }

  private void createPromotedOrder(OrderSagaStepRow step) {
    OrderSagaStepRow release = sagaMapper.findPromotionResult(step.getOrderId(), step.getSagaType());
    if (release != null && release.getPromotedTicketId() != null && release.getPromotedCapacityTokenId() != null) {
      orderService.createOrderFromTicket(release.getPromotedTicketId(), release.getPromotedCapacityTokenId());
    }
  }

  private void completeCancellation(OrderRow order) {
    transactionTemplate.executeWithoutResult(status -> {
      OrderRow current = requireOrder(order.getId());
      OrderStatus currentStatus = OrderStatus.valueOf(current.getStatus());
      if (currentStatus == OrderStatus.CANCELLED) {
        return;
      }
      if (currentStatus != OrderStatus.PENDING_PAYMENT && currentStatus != OrderStatus.WAIT_MERCHANT_ACCEPT) {
        throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be cancelled");
      }
      updateAndAppend(current, OrderStatus.CANCELLED, "OrderCancelled");
    });
  }

  private void completeOrder(OrderRow order, OrderStatus expected, OrderStatus target, String eventType) {
    transactionTemplate.executeWithoutResult(status -> {
      OrderRow current = requireOrder(order.getId());
      OrderStatus currentStatus = OrderStatus.valueOf(current.getStatus());
      if (currentStatus == target) {
        return;
      }
      if (currentStatus != expected) {
        throw new BizException(ErrorCode.ILLEGAL_STATUS, "order status changed while saga was running");
      }
      updateAndAppend(current, target, eventType);
    });
  }

  private void updateAndAppend(OrderRow order, OrderStatus status, String eventType) {
    LocalDateTime now = LocalDateTime.now();
    if (orderMapper.updateStatusIfCurrent(order.getId(), order.getStatus(), status.name(), now) != 1) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order status changed concurrently");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("orderId", order.getId());
    payload.put("userId", order.getUserId());
    payload.put("merchantId", order.getMerchantId());
    payload.put("status", status.name());
    payload.put("queueTicketId", order.getQueueTicketId());
    payload.put("capacityTokenId", order.getCapacityTokenId());
    payload.put("payOrderId", order.getPayOrderId());
    payload.put("amountCent", order.getAmountCent());
    localEventMapper.insert(idGenerator.next("order_local_event"),
        EventKey.of("order", eventType, order.getId(), 1), eventType, 1, "ORDER", order.getId(),
        toJson(payload), LocalEventStatus.NEW.name(), now);
  }

  private void insertSteps(OrderRow order, String sagaType, String reason, String... steps) {
    for (int i = 0; i < steps.length; i++) {
      insertStep(order.getId(), order.getPayOrderId(), sagaType, steps[i], i + 1, reason);
    }
  }

  private void insertStep(long orderId, long payOrderId, String sagaType, String stepName, int stepOrder,
      String reason) {
    sagaMapper.insertStep(idGenerator.next("order_saga_step"), orderId, payOrderId, sagaType, stepName, stepOrder,
        reason, LocalDateTime.now());
  }

  private OrderRow requireOrder(long orderId) {
    OrderRow row = orderMapper.findById(orderId);
    if (row == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "order not found");
    }
    return row;
  }

  private List<Long> reservationIds(OrderRow order) {
    try {
      return objectMapper.readValue(order.getReservationIdsJson(), LONG_LIST);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to deserialize order reservations", ex);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize order saga event", ex);
    }
  }

  private LocalDateTime retryAt(int attempt, LocalDateTime now) {
    return now.plusSeconds(Math.min(300, 1L << Math.min(8, Math.max(0, attempt))));
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() <= 512 ? message : message.substring(0, 512);
  }
}
