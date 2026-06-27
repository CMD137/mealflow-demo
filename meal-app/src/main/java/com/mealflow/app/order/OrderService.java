package com.mealflow.app.order;

import com.mealflow.app.catalog.CatalogService;
import com.mealflow.app.catalog.OrderItemSnapshot;
import com.mealflow.app.catalog.OrderSkuItem;
import com.mealflow.app.catalog.ReserveStockCommand;
import com.mealflow.app.payment.PaymentService;
import com.mealflow.app.payment.PaymentSuccessEvent;
import com.mealflow.app.promotion.LockVoucherCommand;
import com.mealflow.app.promotion.PromotionService;
import com.mealflow.app.promotion.VoucherLockResult;
import com.mealflow.app.queue.CapacityToken;
import com.mealflow.app.queue.QueueApplyCommand;
import com.mealflow.app.queue.QueueApplyResponse;
import com.mealflow.app.queue.QueueReadyEvent;
import com.mealflow.app.queue.QueueService;
import com.mealflow.app.queue.QueueTicket;
import com.mealflow.app.queue.QueueTicketSnapshot;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.OrderStatus;
import com.mealflow.infra.event.EventEnvelope;
import com.mealflow.infra.event.EventKey;
import com.mealflow.infra.event.LocalEventStore;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final CatalogService catalogService;
  private final PromotionService promotionService;
  private final QueueService queueService;
  private final PaymentService paymentService;
  private final IdGenerator idGenerator;
  private final IdempotentTemplate idempotentTemplate;
  private final LocalEventStore localEventStore;
  private final Map<Long, OrderRecord> orders = new ConcurrentHashMap<>();
  private final Map<Long, Long> orderIdByTicketId = new ConcurrentHashMap<>();

  public OrderService(CatalogService catalogService, PromotionService promotionService, QueueService queueService,
      PaymentService paymentService, IdGenerator idGenerator, IdempotentTemplate idempotentTemplate,
      LocalEventStore localEventStore) {
    this.catalogService = catalogService;
    this.promotionService = promotionService;
    this.queueService = queueService;
    this.paymentService = paymentService;
    this.idGenerator = idGenerator;
    this.idempotentTemplate = idempotentTemplate;
    this.localEventStore = localEventStore;
  }

  public SubmitOrderResponse submit(long userId, SubmitOrderRequest request) {
    return idempotentTemplate.execute("order-submit:" + userId + ":" + request.requestId(), () -> {
      LocalDateTime expireTime = LocalDateTime.now().plusMinutes(15);
      List<OrderSkuItem> items = normalizeItems(request);
      List<OrderItemSnapshot> snapshots = catalogService.buildSnapshots(request.merchantId(), items);
      int originAmount = snapshots.stream().mapToInt(OrderItemSnapshot::subtotalCent).sum();
      List<Long> reservationIds = catalogService.reserve(new ReserveStockCommand(
          "stock-reserve:" + request.requestId(), userId, request.merchantId(), items, expireTime));
      VoucherLockResult voucherLock = promotionService.lock(new LockVoucherCommand(
          "voucher-lock:" + request.requestId(), userId, request.userVoucherId(), null, null, expireTime));
      int finalAmount = Math.max(0, originAmount - voucherLock.discountAmount());
      QueueTicketSnapshot queueSnapshot = new QueueTicketSnapshot(
          snapshots, reservationIds, voucherLock.voucherLockId(), finalAmount, request.remark());
      QueueApplyResponse queueResult;
      try {
        queueResult = queueService.apply(new QueueApplyCommand(
            "queue-apply:" + request.requestId(), userId, request.merchantId(), queueSnapshot, expireTime, 0));
      } catch (RuntimeException ex) {
        catalogService.release(reservationIds);
        promotionService.release(voucherLock.voucherLockId());
        throw ex;
      }

      if ("QUEUED".equals(queueResult.result())) {
        catalogService.bindToTicket(reservationIds, queueResult.ticketId());
        promotionService.bindToTicket(voucherLock.voucherLockId(), queueResult.ticketId());
        return SubmitOrderResponse.queued(queueResult.ticketId(), queueResult.ticketNo(), queueResult.aheadCount(),
            queueResult.estimatedWaitSeconds(), queueResult.expireTime());
      }

      OrderRecord order = createOrder(userId, request.merchantId(), null, queueResult.capacityTokenId(), queueSnapshot);
      return SubmitOrderResponse.orderCreated(order.id, order.payOrderId, order.status.name());
    });
  }

  @EventListener
  public void handleQueueReady(QueueReadyEvent event) {
    idempotentTemplate.execute("order-from-ticket:" + event.ticketId(), () -> {
      QueueTicket ticket = queueService.markProcessing(event.ticketId());
      createOrder(ticket.userId, ticket.merchantId, ticket.id, event.capacityTokenId(), ticket.snapshot);
      return true;
    });
  }

  @EventListener
  public void handlePaymentSuccess(PaymentSuccessEvent event) {
    synchronized (this) {
      OrderRecord order = requireOrder(event.orderId());
      if (order.status == OrderStatus.PENDING_PAYMENT) {
        order.status = OrderStatus.WAIT_MERCHANT_ACCEPT;
        catalogService.confirm(order.reservationIds);
        promotionService.confirm(order.voucherLockId);
        appendEvent("payment", "PaymentSuccess", event.payOrderId(), "PAYMENT", event.payOrderId(),
            Map.of("orderId", order.id));
      }
    }
  }

  public synchronized OrderView get(long orderId) {
    return view(requireOrder(orderId));
  }

  public synchronized void cancel(long orderId, String reason) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.PENDING_PAYMENT && order.status != OrderStatus.WAIT_MERCHANT_ACCEPT) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "当前订单状态不可取消");
    }
    order.status = OrderStatus.CANCELLED;
    catalogService.release(order.reservationIds);
    promotionService.release(order.voucherLockId);
    paymentService.close(order.payOrderId);
    queueService.findTokenByOrder(orderId)
        .ifPresent(token -> queueService.releaseCapacity(token.id, "ORDER_CANCELLED"));
    appendEvent("order", "OrderCancelled", order.id, "ORDER", order.id, Map.of("reason", reason == null ? "" : reason));
  }

  public synchronized void merchantAccept(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.WAIT_MERCHANT_ACCEPT) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "订单不是待接单状态");
    }
    order.status = OrderStatus.MERCHANT_ACCEPTED;
  }

  public synchronized void mealReady(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.MERCHANT_ACCEPTED && order.status != OrderStatus.COOKING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "订单不能出餐");
    }
    order.status = OrderStatus.WAIT_RIDER_PICKUP;
  }

  public synchronized void pickedUp(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.WAIT_RIDER_PICKUP) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "订单不能取餐");
    }
    order.status = OrderStatus.DELIVERING;
  }

  public synchronized void delivered(long orderId) {
    OrderRecord order = requireOrder(orderId);
    if (order.status != OrderStatus.DELIVERING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "订单不能送达");
    }
    order.status = OrderStatus.COMPLETED;
  }

  public List<OrderView> orders() {
    return orders.values().stream()
        .sorted(Comparator.comparingLong(order -> order.id))
        .map(this::view)
        .toList();
  }

  private synchronized OrderRecord createOrder(long userId, long merchantId, Long ticketId, long capacityTokenId,
      QueueTicketSnapshot snapshot) {
    if (ticketId != null && orderIdByTicketId.containsKey(ticketId)) {
      return requireOrder(orderIdByTicketId.get(ticketId));
    }
    long orderId = idGenerator.next("order");
    PaymentService.PaymentOrder payment = paymentService.createForOrder(orderId, snapshot.totalAmount());
    OrderRecord order = new OrderRecord(orderId, userId, merchantId, OrderStatus.PENDING_PAYMENT, ticketId,
        capacityTokenId, payment.id, snapshot.reservationIds(), snapshot.voucherLockId(), snapshot.items(),
        snapshot.totalAmount());
    orders.put(orderId, order);
    if (ticketId != null) {
      orderIdByTicketId.put(ticketId, orderId);
      queueService.confirmOrderCreated(ticketId, orderId);
    }
    queueService.bindTokenOrder(capacityTokenId, orderId);
    catalogService.bindToOrder(snapshot.reservationIds(), orderId);
    promotionService.bindToOrder(snapshot.voucherLockId(), orderId);
    appendEvent("order", ticketId == null ? "OrderCreated" : "OrderCreatedFromTicket",
        ticketId == null ? orderId : ticketId, "ORDER", orderId, Map.of("orderId", orderId));
    return order;
  }

  private List<OrderSkuItem> normalizeItems(SubmitOrderRequest request) {
    if (request.items() != null && !request.items().isEmpty()) {
      return request.items();
    }
    if (request.cartItemIds() != null && !request.cartItemIds().isEmpty()) {
      return request.cartItemIds().stream().map(skuId -> new OrderSkuItem(skuId, 1)).toList();
    }
    throw new BizException(ErrorCode.BAD_REQUEST, "请至少提交一个商品");
  }

  private OrderRecord requireOrder(long orderId) {
    OrderRecord order = orders.get(orderId);
    if (order == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
    }
    return order;
  }

  private OrderView view(OrderRecord order) {
    return new OrderView(order.id, order.userId, order.merchantId, order.status.name(), order.queueTicketId,
        order.capacityTokenId, order.payOrderId, order.amountCent, order.items);
  }

  private void appendEvent(String producer, String eventType, Object businessKey, String aggregateType,
      long aggregateId, Map<String, Object> payload) {
    long eventId = idGenerator.next("localEvent");
    String key = EventKey.of(producer, eventType, businessKey, 1);
    localEventStore.append(new EventEnvelope(eventId, key, eventType + "Event", 1, aggregateType, aggregateId,
        "local", LocalDateTime.now(), new HashMap<>(payload)));
    localEventStore.markSent(key);
  }

  static class OrderRecord {
    final long id;
    final long userId;
    final long merchantId;
    OrderStatus status;
    final Long queueTicketId;
    final long capacityTokenId;
    final long payOrderId;
    final List<Long> reservationIds;
    final Long voucherLockId;
    final List<OrderItemSnapshot> items;
    final int amountCent;

    OrderRecord(long id, long userId, long merchantId, OrderStatus status, Long queueTicketId, long capacityTokenId,
        long payOrderId, List<Long> reservationIds, Long voucherLockId, List<OrderItemSnapshot> items,
        int amountCent) {
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
  }
}
