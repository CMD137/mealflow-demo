package com.mealflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.PageResult;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.ConsumerRecordStatus;
import com.mealflow.common.status.LocalEventStatus;
import com.mealflow.common.status.OrderStatus;
import com.mealflow.infra.consumer.PersistentConsumerRecordTemplate;
import com.mealflow.infra.event.EventKey;
import com.mealflow.order.api.AdminOrderQuery;
import com.mealflow.order.api.LocalEventView;
import com.mealflow.order.api.OrderItemSnapshot;
import com.mealflow.order.api.OrderSkuItem;
import com.mealflow.order.api.OrderStatisticsView;
import com.mealflow.order.api.OrderView;
import com.mealflow.order.api.SubmitOrderRequest;
import com.mealflow.order.api.SubmitOrderResponse;
import com.mealflow.order.client.CatalogClient;
import com.mealflow.order.client.AuthUserClient;
import com.mealflow.order.client.MerchantClient;
import com.mealflow.order.client.PaymentClient;
import com.mealflow.order.client.PromotionClient;
import com.mealflow.order.client.QueueClient;
import com.mealflow.order.mapper.ConsumerRecordMapper;
import com.mealflow.order.mapper.ConsumerRecordRow;
import com.mealflow.order.mapper.LocalEventMapper;
import com.mealflow.order.mapper.LocalEventRow;
import com.mealflow.order.mapper.OrderMapper;
import com.mealflow.order.mapper.OrderRow;
import com.mealflow.order.mapper.StatusCountRow;
import com.mealflow.order.outbox.OutboxEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final Duration OUTBOX_SENDING_TIMEOUT = Duration.ofMinutes(1);
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<OrderItemSnapshot>> ITEM_LIST = new TypeReference<>() {
  };
  private static final TypeReference<Map<String, Object>> EVENT_PAYLOAD = new TypeReference<>() {
  };

  private final CatalogClient catalogClient;
  private final AuthUserClient authUserClient;
  private final MerchantClient merchantClient;
  private final PromotionClient promotionClient;
  private final QueueClient queueClient;
  private final PaymentClient paymentClient;
  private final OrderMapper orderMapper;
  private final LocalEventMapper localEventMapper;
  private final ConsumerRecordMapper consumerRecordMapper;
  private final PersistentConsumerRecordTemplate consumerRecordTemplate;
  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;
  private final DatabaseIdGenerator idGenerator;
  private final OrderSagaCoordinator sagaCoordinator;

  public OrderService(CatalogClient catalogClient, AuthUserClient authUserClient, MerchantClient merchantClient, PromotionClient promotionClient, QueueClient queueClient,
      PaymentClient paymentClient, OrderMapper orderMapper, LocalEventMapper localEventMapper,
      ConsumerRecordMapper consumerRecordMapper, OutboxEventPublisher outboxEventPublisher, ObjectMapper objectMapper,
      DatabaseIdGenerator idGenerator, OrderSagaCoordinator sagaCoordinator) {
    this.catalogClient = catalogClient;
    this.authUserClient = authUserClient;
    this.merchantClient = merchantClient;
    this.promotionClient = promotionClient;
    this.queueClient = queueClient;
    this.paymentClient = paymentClient;
    this.orderMapper = orderMapper;
    this.localEventMapper = localEventMapper;
    this.consumerRecordMapper = consumerRecordMapper;
    this.consumerRecordTemplate = new PersistentConsumerRecordTemplate(consumerRecordMapper,
        () -> idGenerator.next("order_consumer_record"));
    this.outboxEventPublisher = outboxEventPublisher;
    this.objectMapper = objectMapper;
    this.idGenerator = idGenerator;
    this.sagaCoordinator = sagaCoordinator;
  }

  @Transactional
  public SubmitOrderResponse submitInTransaction(long userId, SubmitOrderRequest request) {
    LocalDateTime expireTime = LocalDateTime.now().plusMinutes(15);
    List<OrderSkuItem> items = normalizeItems(request);
    if (request.addressId() == null) {
      throw new BizException(ErrorCode.BAD_REQUEST, "delivery address is required");
    }
    MerchantClient.MerchantView merchant = merchantClient.requireAcceptingOrders(request.merchantId());
    int effectiveCapacity = Math.max(1, (int) Math.round(merchant.baseCapacity() * merchant.manualFactor()));
    AuthUserClient.AddressView address = authUserClient.address(userId, request.addressId());
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
        reservation.reservationIds(), voucherLock.voucherLockId(), finalAmount, request.remark(), userId,
        request.merchantId(), address.contactName(), address.phone(), address.detail());
    QueueClient.QueueApplyResponse queue = queueClient.apply(new QueueClient.QueueApplyRequest(
        "queue-apply:" + request.requestId(), userId, request.merchantId(), snapshot, expireTime, 0,
        effectiveCapacity));
    if ("QUEUED".equals(queue.result())) {
      return SubmitOrderResponse.queued(queue.ticketId(), queue.ticketNo(), queue.aheadCount(),
          queue.estimatedWaitSeconds(), queue.expireTime());
    }
    OrderRecord order = createOrder(userId, request.merchantId(), null, queue.capacityTokenId(), snapshot);
    return SubmitOrderResponse.orderCreated(order.id, order.payOrderId, order.status.name());
  }

  @Transactional
  public synchronized OrderRecord createOrderFromTicket(long ticketId, long capacityTokenId) {
    Optional<OrderRecord> existing = findOrderByTicket(ticketId);
    if (existing.isPresent()) {
      return existing.get();
    }
    QueueClient.QueueTicketSnapshot snapshot = queueClient.markProcessing(ticketId);
    if (snapshot.userId() <= 0 || snapshot.merchantId() <= 0) {
      throw new IllegalStateException("queue ticket snapshot is missing its original principal");
    }
    // Ticket promotion must preserve the original customer and merchant; it is never a system-owned order.
    OrderRecord order = createOrder(snapshot.userId(), snapshot.merchantId(), ticketId, capacityTokenId, snapshot);
    queueClient.orderCreated(ticketId, new QueueClient.BindOrderRequest("queue-order-created:" + ticketId + ":" + order.id,
        order.id));
    return order;
  }

  public synchronized void markPaid(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status == OrderStatus.PENDING_PAYMENT) {
      sagaCoordinator.enqueuePaymentSuccess(orderMapper.findById(orderId));
      sagaCoordinator.processReadyForOrder(orderId);
    }
  }

  @Transactional
  public Boolean consumePaymentPaid(String eventKey, String consumerGroup, String payloadJson) {
    return consumerRecordTemplate.consumeOnce(eventKey, consumerGroup, "PaymentPaid", payloadJson, () -> {
      Map<String, Object> payload = fromJson(payloadJson, EVENT_PAYLOAD);
      markPaid(longNumber(payload.get("orderId")));
      return Boolean.TRUE;
    });
  }

  public int recoverTimedOutConsumerRecords() {
    return consumerRecordTemplate.recoverProcessingTimeouts();
  }

  @Transactional
  public Boolean replayPaymentConsumerRecord(String eventKey, String consumerGroup) {
    ConsumerRecordRow row = requireConsumerRecord(eventKey, consumerGroup);
    if (ConsumerRecordStatus.SUCCESS.name().equals(row.getStatus())) {
      return null;
    }
    if (!"PaymentPaid".equals(row.getEventType()) || row.getPayloadJson() == null || row.getPayloadJson().isBlank()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "consumer record payload is not replayable");
    }
    return consumePaymentPaid(eventKey, consumerGroup, row.getPayloadJson());
  }

  public synchronized void cancel(long orderId, String reason) {
    OrderRecord order = requireOrder(orderId);
    sagaCoordinator.enqueueCancellation(orderMapper.findById(orderId), reason);
    sagaCoordinator.processReadyForOrder(orderId);
  }

  /** Uses the same cancellation saga as a user cancellation, so every remote release remains idempotent. */
  public int expirePendingPayments(int limit) {
    int expired = 0;
    for (OrderRow row : orderMapper.findExpiredPendingPayments(LocalDateTime.now(), Math.max(1, limit))) {
      try {
        cancel(row.getId(), "PAYMENT_TIMEOUT");
        expired++;
      } catch (BizException ex) {
        // A concurrent payment/cancellation wins through status conditions; a later scan need not retry it.
        if (ex.errorCode() != ErrorCode.ILLEGAL_STATUS) {
          throw ex;
        }
      }
    }
    return expired;
  }

  @Transactional
  public synchronized void merchantAccept(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (sagaCoordinator.hasIncompleteCancellation(orderId)) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cancellation is in progress");
    }
    if (order.status != OrderStatus.WAIT_MERCHANT_ACCEPT) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order is not waiting merchant accept");
    }
    updateStatus(orderId, order.status, OrderStatus.MERCHANT_ACCEPTED);
    appendOrderEvent("OrderMerchantAccepted", order.withStatus(OrderStatus.MERCHANT_ACCEPTED));
  }

  @Transactional
  public synchronized void mealReady(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status == OrderStatus.WAIT_RIDER_PICKUP) {
      return;
    }
    if (order.status != OrderStatus.MERCHANT_ACCEPTED && order.status != OrderStatus.COOKING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be marked meal ready");
    }
    updateStatus(orderId, order.status, OrderStatus.WAIT_RIDER_PICKUP);
    appendOrderEvent("OrderMealReady", order.withStatus(OrderStatus.WAIT_RIDER_PICKUP));
  }

  @Transactional
  public synchronized void pickedUp(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.WAIT_RIDER_PICKUP) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be picked up");
    }
    updateStatus(orderId, order.status, OrderStatus.DELIVERING);
    appendOrderEvent("OrderPickedUp", order.withStatus(OrderStatus.DELIVERING));
  }

  @Transactional
  public synchronized void delivered(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.DELIVERING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order cannot be delivered");
    }
    updateStatus(orderId, order.status, OrderStatus.COMPLETED);
    appendOrderEvent("OrderDelivered", order.withStatus(OrderStatus.COMPLETED));
  }

  public OrderView get(long orderId) {
    return view(requireOrder(orderId));
  }

  public List<OrderView> list() {
    return orderMapper.findAll().stream().map(this::mapOrder).map(this::view).toList();
  }

  public List<OrderView> listByUser(long userId) {
    return orderMapper.findByUserId(userId).stream().map(this::mapOrder).map(this::view).toList();
  }

  public PageResult<OrderView> adminOrders(AdminOrderQuery query) {
    long total = orderMapper.countAdminOrders(query.merchantId(), query.userId(), query.status(), query.beginTime(),
        query.endTime());
    List<OrderView> items = orderMapper.findAdminOrders(query.merchantId(), query.userId(), query.status(),
        query.beginTime(), query.endTime(), query.pageSize(), query.offset()).stream()
        .map(this::mapOrder)
        .map(this::view)
        .toList();
    return PageResult.of(items, total, query.page(), query.pageSize());
  }

  public OrderStatisticsView adminStatistics(AdminOrderQuery query) {
    Map<String, Long> counts = orderMapper.countByStatus(query.merchantId(), query.beginTime(), query.endTime())
        .stream()
        .collect(java.util.stream.Collectors.toMap(StatusCountRow::getStatus, StatusCountRow::getCount));
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    long waitingAccept = counts.getOrDefault(OrderStatus.WAIT_MERCHANT_ACCEPT.name(), 0L);
    long accepted = counts.getOrDefault(OrderStatus.MERCHANT_ACCEPTED.name(), 0L)
        + counts.getOrDefault(OrderStatus.COOKING.name(), 0L)
        + counts.getOrDefault(OrderStatus.WAIT_RIDER_PICKUP.name(), 0L);
    long delivering = counts.getOrDefault(OrderStatus.DELIVERING.name(), 0L);
    long completed = counts.getOrDefault(OrderStatus.COMPLETED.name(), 0L);
    long cancelled = counts.getOrDefault(OrderStatus.CANCELLED.name(), 0L);
    int turnoverCent = orderMapper.sumCompletedAmount(query.merchantId(), query.beginTime(), query.endTime());
    return new OrderStatisticsView(total, waitingAccept, accepted, delivering, completed, cancelled, turnoverCent);
  }

  public List<LocalEventView> events() {
    return localEventMapper.findAll().stream().map(this::eventView).toList();
  }

  public int dispatchPendingEvents(int limit) {
    recoverStaleSendingEvents();
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

  public int recoverStaleSendingEvents() {
    LocalDateTime now = LocalDateTime.now();
    return localEventMapper.markStaleSendingFailedBefore(now.minus(OUTBOX_SENDING_TIMEOUT), now);
  }

  private synchronized OrderRecord createOrder(long userId, long merchantId, Long ticketId, long capacityTokenId,
      QueueClient.QueueTicketSnapshot snapshot) {
    long orderId = idGenerator.next("order");
    PaymentClient.PaymentView payment = paymentClient.create(new PaymentClient.CreatePaymentRequest(
        "payment-create:" + orderId, orderId, userId, snapshot.totalAmount()));
    List<OrderItemSnapshot> items = snapshot.items().stream().map(this::toOrderItemSnapshot).toList();
    LocalDateTime paymentExpireTime = LocalDateTime.now().plusMinutes(15);
    OrderRecord order = new OrderRecord(orderId, userId, merchantId, OrderStatus.PENDING_PAYMENT, ticketId,
        capacityTokenId, payment.payOrderId(), snapshot.reservationIds(), snapshot.voucherLockId(), items,
        snapshot.totalAmount(), snapshot.contactName(), snapshot.contactPhone(), snapshot.deliveryAddress(),
        paymentExpireTime);
    LocalDateTime now = LocalDateTime.now();
    try {
      orderMapper.insert(order.id, order.userId, order.merchantId, order.status.name(), order.queueTicketId,
          order.capacityTokenId, order.payOrderId, toJson(order.reservationIds), order.voucherLockId,
          toJson(order.items), order.amountCent, order.contactName, order.contactPhone, order.deliveryAddress,
          order.paymentExpireTime, now);
      queueClient.bindOrder(capacityTokenId, new QueueClient.BindOrderRequest("bind-token-order:" + orderId, orderId));
    } catch (RuntimeException failure) {
      // The payment service is outside this transaction; close it before the local order insert rolls back.
      try {
        paymentClient.close(payment.payOrderId(), new PaymentClient.ClosePaymentRequest(
            "payment-close-orphan:" + orderId, "ORDER_CREATE_FAILED"));
      } catch (RuntimeException closeFailure) {
        failure.addSuppressed(closeFailure);
        try {
          sagaCoordinator.enqueueOrphanPayment(orderId, payment.payOrderId());
        } catch (RuntimeException recoveryFailure) {
          failure.addSuppressed(recoveryFailure);
        }
      }
      throw failure;
    }
    appendOrderEvent("OrderCreated", order);
    return order;
  }

  private void updateStatus(long orderId, OrderStatus expected, OrderStatus target) {
    if (orderMapper.updateStatusIfCurrent(orderId, expected.name(), target.name(), LocalDateTime.now()) != 1) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "order status changed concurrently");
    }
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

  private long longNumber(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
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

  private ConsumerRecordRow requireConsumerRecord(String eventKey, String consumerGroup) {
    ConsumerRecordRow row = consumerRecordMapper.findByEvent(eventKey, consumerGroup);
    if (row == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "consumer record not found");
    }
    return row;
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
        fromJson(row.getItemsJson(), ITEM_LIST), row.getAmountCent(), row.getContactName(), row.getContactPhone(),
        row.getDeliveryAddress(), row.getPaymentExpireTime());
  }

  private OrderView view(OrderRecord order) {
    return new OrderView(order.id, order.userId, order.merchantId, order.status.name(), order.queueTicketId,
        order.capacityTokenId, order.payOrderId, order.amountCent, order.items, order.contactName,
        order.contactPhone, order.deliveryAddress);
  }

  private void appendOrderEvent(String eventType, OrderRecord order) {
    int version = 1;
    localEventMapper.insert(idGenerator.next("order_local_event"),
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
    final String contactName;
    final String contactPhone;
    final String deliveryAddress;
    final LocalDateTime paymentExpireTime;

    OrderRecord(long id, long userId, long merchantId, OrderStatus status, Long queueTicketId,
        long capacityTokenId, long payOrderId, List<Long> reservationIds, Long voucherLockId,
        List<OrderItemSnapshot> items, int amountCent, String contactName, String contactPhone,
        String deliveryAddress, LocalDateTime paymentExpireTime) {
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
      this.contactName = contactName;
      this.contactPhone = contactPhone;
      this.deliveryAddress = deliveryAddress;
      this.paymentExpireTime = paymentExpireTime;
    }

    OrderRecord withStatus(OrderStatus nextStatus) {
      return new OrderRecord(id, userId, merchantId, nextStatus, queueTicketId, capacityTokenId, payOrderId,
          reservationIds, voucherLockId, items, amountCent, contactName, contactPhone, deliveryAddress,
          paymentExpireTime);
    }
  }
}
