package com.mealflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.LocalEventStatus;
import com.mealflow.common.status.OrderStatus;
import com.mealflow.infra.event.EventKey;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.order.api.LocalEventView;
import com.mealflow.order.api.OrderItemSnapshot;
import com.mealflow.order.api.OrderSkuItem;
import com.mealflow.order.api.OrderView;
import com.mealflow.order.api.SubmitOrderRequest;
import com.mealflow.order.api.SubmitOrderResponse;
import com.mealflow.order.client.CatalogClient;
import com.mealflow.order.client.PaymentClient;
import com.mealflow.order.client.PromotionClient;
import com.mealflow.order.client.QueueClient;
import com.mealflow.order.mapper.LocalEventMapper;
import com.mealflow.order.mapper.LocalEventRow;
import com.mealflow.order.mapper.OrderMapper;
import com.mealflow.order.mapper.OrderRow;
import com.mealflow.order.outbox.OutboxEventPublisher;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<OrderItemSnapshot>> ITEM_LIST = new TypeReference<>() {
  };

  private final CatalogClient catalogClient;
  private final PromotionClient promotionClient;
  private final QueueClient queueClient;
  private final PaymentClient paymentClient;
  private final OrderMapper orderMapper;
  private final LocalEventMapper localEventMapper;
  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();

  public OrderService(CatalogClient catalogClient, PromotionClient promotionClient, QueueClient queueClient,
      PaymentClient paymentClient, OrderMapper orderMapper, LocalEventMapper localEventMapper,
      OutboxEventPublisher outboxEventPublisher, ObjectMapper objectMapper) {
    this.catalogClient = catalogClient;
    this.promotionClient = promotionClient;
    this.queueClient = queueClient;
    this.paymentClient = paymentClient;
    this.orderMapper = orderMapper;
    this.localEventMapper = localEventMapper;
    this.outboxEventPublisher = outboxEventPublisher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public SubmitOrderResponse submit(long userId, SubmitOrderRequest request) {
    return idempotentTemplate.execute("order:submit:" + userId + ":" + request.requestId(), () -> {
      LocalDateTime expireTime = LocalDateTime.now().plusMinutes(15);
      List<OrderSkuItem> items = normalizeItems(request);
      List<OrderItemSnapshot> snapshots = catalogClient.snapshots(request.merchantId(), items);
      int originAmount = snapshots.stream().mapToInt(OrderItemSnapshot::subtotalCent).sum();
      CatalogClient.ReserveStockResponse reservation = catalogClient.reserve(new CatalogClient.ReserveStockRequest(
          "stock-reserve:" + request.requestId(), userId, request.merchantId(), null, null, items, expireTime));
      PromotionClient.VoucherLockResponse voucherLock = promotionClient.lock(new PromotionClient.LockVoucherRequest(
          "voucher-lock:" + request.requestId(), userId, request.userVoucherId(), null, null, expireTime));
      int finalAmount = Math.max(0, originAmount - voucherLock.discountAmount());
      QueueClient.QueueTicketSnapshot snapshot = new QueueClient.QueueTicketSnapshot(
          snapshots.stream().map(item -> Map.<String, Object>of(
              "skuId", item.skuId(),
              "skuName", item.skuName(),
              "priceCent", item.priceCent(),
              "quantity", item.quantity())).toList(),
          reservation.reservationIds(), voucherLock.voucherLockId(), finalAmount, request.remark());
      QueueClient.QueueApplyResponse queue = queueClient.apply(new QueueClient.QueueApplyRequest(
          "queue-apply:" + request.requestId(), userId, request.merchantId(), snapshot, expireTime, 0));
      if ("QUEUED".equals(queue.result())) {
        return SubmitOrderResponse.queued(queue.ticketId(), queue.ticketNo(), queue.aheadCount(),
            queue.estimatedWaitSeconds(), queue.expireTime());
      }
      OrderRecord order = createOrder(userId, request.merchantId(), null, queue.capacityTokenId(), snapshot);
      return SubmitOrderResponse.orderCreated(order.id, order.payOrderId, order.status.name());
    });
  }

  @Transactional
  public synchronized OrderRecord createOrderFromTicket(long ticketId, long capacityTokenId) {
    Optional<OrderRecord> existing = findOrderByTicket(ticketId);
    if (existing.isPresent()) {
      return existing.get();
    }
    QueueClient.QueueTicketSnapshot snapshot = queueClient.markProcessing(ticketId);
    OrderRecord order = createOrder(0L, 10L, ticketId, capacityTokenId, snapshot);
    queueClient.orderCreated(ticketId, new QueueClient.BindOrderRequest("queue-order-created:" + ticketId + ":" + order.id,
        order.id));
    return order;
  }

  @Transactional
  public synchronized void markPaid(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status == OrderStatus.PENDING_PAYMENT) {
      updateStatus(orderId, OrderStatus.WAIT_MERCHANT_ACCEPT);
      catalogClient.confirm(new CatalogClient.StockTransitionRequest("stock-confirm:" + orderId, order.reservationIds,
          orderId, "PAYMENT_SUCCESS"));
      promotionClient.confirm(new PromotionClient.VoucherTransitionRequest("voucher-confirm:" + orderId,
          order.voucherLockId, orderId, "PAYMENT_SUCCESS"));
      appendOrderEvent("OrderPaid", order.withStatus(OrderStatus.WAIT_MERCHANT_ACCEPT));
    }
  }

  @Transactional
  public synchronized void cancel(long orderId, String reason) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.PENDING_PAYMENT && order.status != OrderStatus.WAIT_MERCHANT_ACCEPT) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be cancelled");
    }
    updateStatus(orderId, OrderStatus.CANCELLED);
    catalogClient.release(new CatalogClient.StockTransitionRequest("stock-release:" + orderId, order.reservationIds,
        orderId, reason));
    promotionClient.release(new PromotionClient.VoucherTransitionRequest("voucher-release:" + orderId,
        order.voucherLockId, orderId, reason));
    paymentClient.close(order.payOrderId, new PaymentClient.ClosePaymentRequest("payment-close:" + orderId, reason));
    queueClient.release(order.capacityTokenId, new QueueClient.ReleaseCapacityRequest("capacity-release:" + orderId,
        "ORDER_CANCELLED"));
    appendOrderEvent("OrderCancelled", order.withStatus(OrderStatus.CANCELLED));
  }

  @Transactional
  public synchronized void merchantAccept(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.WAIT_MERCHANT_ACCEPT) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order is not waiting merchant accept");
    }
    updateStatus(orderId, OrderStatus.MERCHANT_ACCEPTED);
    appendOrderEvent("OrderMerchantAccepted", order.withStatus(OrderStatus.MERCHANT_ACCEPTED));
  }

  @Transactional
  public synchronized void mealReady(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.MERCHANT_ACCEPTED && order.status != OrderStatus.COOKING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be marked meal ready");
    }
    updateStatus(orderId, OrderStatus.WAIT_RIDER_PICKUP);
    appendOrderEvent("OrderMealReady", order.withStatus(OrderStatus.WAIT_RIDER_PICKUP));
  }

  @Transactional
  public synchronized void pickedUp(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.WAIT_RIDER_PICKUP) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be picked up");
    }
    updateStatus(orderId, OrderStatus.DELIVERING);
    appendOrderEvent("OrderPickedUp", order.withStatus(OrderStatus.DELIVERING));
  }

  @Transactional
  public synchronized void delivered(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.DELIVERING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be delivered");
    }
    updateStatus(orderId, OrderStatus.COMPLETED);
    appendOrderEvent("OrderDelivered", order.withStatus(OrderStatus.COMPLETED));
  }

  public OrderView get(long orderId) {
    return view(requireOrder(orderId));
  }

  public List<OrderView> list() {
    return orderMapper.findAll().stream().map(this::mapOrder).map(this::view).toList();
  }

  public List<LocalEventView> events() {
    return localEventMapper.findAll().stream().map(this::eventView).toList();
  }

  public int dispatchPendingEvents(int limit) {
    int sent = 0;
    for (LocalEventRow row : localEventMapper.findDispatchable(limit)) {
      if (localEventMapper.markSending(row.getId(), LocalDateTime.now()) == 0) {
        continue;
      }
      try {
        outboxEventPublisher.publish(eventView(row));
        localEventMapper.markSent(row.getId(), LocalDateTime.now());
        sent++;
      } catch (RuntimeException ex) {
        localEventMapper.markFailed(row.getId(), trimError(ex), LocalDateTime.now());
      }
    }
    return sent;
  }

  private synchronized OrderRecord createOrder(long userId, long merchantId, Long ticketId, long capacityTokenId,
      QueueClient.QueueTicketSnapshot snapshot) {
    long orderId = idGenerator.next("order");
    PaymentClient.PaymentView payment = paymentClient.create(new PaymentClient.CreatePaymentRequest(
        "payment-create:" + orderId, orderId, snapshot.totalAmount()));
    List<OrderItemSnapshot> items = snapshot.items().stream().map(this::toOrderItemSnapshot).toList();
    OrderRecord order = new OrderRecord(orderId, userId, merchantId, OrderStatus.PENDING_PAYMENT, ticketId,
        capacityTokenId, payment.payOrderId(), snapshot.reservationIds(), snapshot.voucherLockId(), items,
        snapshot.totalAmount());
    LocalDateTime now = LocalDateTime.now();
    orderMapper.insert(order.id, order.userId, order.merchantId, order.status.name(), order.queueTicketId,
        order.capacityTokenId, order.payOrderId, toJson(order.reservationIds), order.voucherLockId,
        toJson(order.items), order.amountCent, now);
    queueClient.bindOrder(capacityTokenId, new QueueClient.BindOrderRequest("bind-token-order:" + orderId, orderId));
    appendOrderEvent("OrderCreated", order);
    return order;
  }

  private void updateStatus(long orderId, OrderStatus status) {
    orderMapper.updateStatus(orderId, status.name(), LocalDateTime.now());
  }

  private OrderItemSnapshot toOrderItemSnapshot(Map<String, Object> item) {
    return new OrderItemSnapshot(number(item.get("skuId")), String.valueOf(item.get("skuName")),
        number(item.get("priceCent")), number(item.get("quantity")));
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private List<OrderSkuItem> normalizeItems(SubmitOrderRequest request) {
    if (request.items() != null && !request.items().isEmpty()) {
      return request.items();
    }
    if (request.cartItemIds() != null && !request.cartItemIds().isEmpty()) {
      return request.cartItemIds().stream().map(skuId -> new OrderSkuItem(skuId, 1)).toList();
    }
    throw new BizException(ErrorCode.BAD_REQUEST, "at least one item is required");
  }

  private OrderRecord requireOrder(long orderId) {
    return findOrder(orderId).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "order not found"));
  }

  private Optional<OrderRecord> findOrder(long orderId) {
    return Optional.ofNullable(orderMapper.findById(orderId)).map(this::mapOrder);
  }

  private Optional<OrderRecord> findOrderByTicket(long ticketId) {
    return Optional.ofNullable(orderMapper.findByTicketId(ticketId)).map(this::mapOrder);
  }

  private OrderRecord mapOrder(OrderRow row) {
    return new OrderRecord(row.getId(), row.getUserId(), row.getMerchantId(),
        OrderStatus.valueOf(row.getStatus()), row.getQueueTicketId(), row.getCapacityTokenId(),
        row.getPayOrderId(), fromJson(row.getReservationIdsJson(), LONG_LIST), row.getVoucherLockId(),
        fromJson(row.getItemsJson(), ITEM_LIST), row.getAmountCent());
  }

  private OrderView view(OrderRecord order) {
    return new OrderView(order.id, order.userId, order.merchantId, order.status.name(), order.queueTicketId,
        order.capacityTokenId, order.payOrderId, order.amountCent, order.items);
  }

  private void appendOrderEvent(String eventType, OrderRecord order) {
    int version = 1;
    localEventMapper.insert(idGenerator.next("localEvent"),
        EventKey.of("order", eventType, order.id, version),
        eventType,
        version,
        "ORDER",
        order.id,
        toJson(orderEventPayload(order)),
        LocalEventStatus.NEW.name(),
        LocalDateTime.now());
  }

  private Map<String, Object> orderEventPayload(OrderRecord order) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("orderId", order.id);
    payload.put("userId", order.userId);
    payload.put("merchantId", order.merchantId);
    payload.put("status", order.status.name());
    payload.put("queueTicketId", order.queueTicketId);
    payload.put("capacityTokenId", order.capacityTokenId);
    payload.put("payOrderId", order.payOrderId);
    payload.put("amountCent", order.amountCent);
    return payload;
  }

  private LocalEventView eventView(LocalEventRow row) {
    return new LocalEventView(row.getId(), row.getEventKey(), row.getEventType(), row.getEventVersion(),
        row.getAggregateType(), row.getAggregateId(), row.getPayloadJson(), row.getStatus(), row.getRetryCount(),
        row.getLastError(), row.getCreateTime(), row.getUpdateTime());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize order data", e);
    }
  }

  private <T> T fromJson(String value, TypeReference<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize order data", e);
    }
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() <= 512 ? message : message.substring(0, 512);
  }

  static class OrderRecord {
    final long id;
    final long userId;
    final long merchantId;
    final OrderStatus status;
    final Long queueTicketId;
    final long capacityTokenId;
    final long payOrderId;
    final List<Long> reservationIds;
    final Long voucherLockId;
    final List<OrderItemSnapshot> items;
    final int amountCent;

    OrderRecord(long id, long userId, long merchantId, OrderStatus status, Long queueTicketId,
        long capacityTokenId, long payOrderId, List<Long> reservationIds, Long voucherLockId,
        List<OrderItemSnapshot> items, int amountCent) {
      this.id = id;
      this.userId = userId;
      this.merchantId = merchantId;
      this.status = status;
      this.queueTicketId = queueTicketId;
      this.capacityTokenId = capacityTokenId;
      this.payOrderId = payOrderId;
      this.reservationIds = reservationIds;
      this.voucherLockId = voucherLockId;
      this.items = items;
      this.amountCent = amountCent;
    }

    OrderRecord withStatus(OrderStatus nextStatus) {
      return new OrderRecord(id, userId, merchantId, nextStatus, queueTicketId, capacityTokenId, payOrderId,
          reservationIds, voucherLockId, items, amountCent);
    }
  }
}
