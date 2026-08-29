package com.mealflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.mealflow.order.api.OrderItemSnapshot;
import com.mealflow.order.api.OrderSkuItem;
import com.mealflow.order.api.AdminOrderQuery;
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
import com.mealflow.order.mapper.OrderSagaMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "mealflow.outbox.scheduler-enabled=false"
    }
)
class OrderPersistenceTest {
  @Autowired
  private OrderService orderService;

  @Autowired
  private ConsumerRecordMapper consumerRecordMapper;

  @Autowired
  private OrderSagaMapper orderSagaMapper;

  @Autowired
  private OrderSagaCoordinator orderSagaCoordinator;

  @MockBean
  private CatalogClient catalogClient;

  @MockBean
  private AuthUserClient authUserClient;

  @MockBean
  private MerchantClient merchantClient;

  @MockBean
  private PromotionClient promotionClient;

  @MockBean
  private QueueClient queueClient;

  @MockBean
  private PaymentClient paymentClient;

  @BeforeEach
  void mockAddress() {
    when(authUserClient.address(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(new AuthUserClient.AddressView(20L, 101L, "Test User", "13800000000", "Test Road 1", true));
  }

  @Test
  void createsAndUpdatesOrderInDatabase() {
    when(catalogClient.snapshots(eq(10L), anyList()))
        .thenReturn(List.of(new OrderItemSnapshot(1L, "测试盖饭", 1000, 2)));
    when(catalogClient.reserve(any()))
        .thenReturn(new CatalogClient.ReserveStockResponse(List.of(8001L), "RESERVED"));
    when(promotionClient.lock(any()))
        .thenReturn(new PromotionClient.VoucherLockResponse(7001L, "LOCKED", 300));
    when(queueClient.apply(any()))
        .thenReturn(new QueueClient.QueueApplyResponse("READY", 6001L, null, null, 0, 0, null));
    when(paymentClient.create(any()))
        .thenReturn(new PaymentClient.PaymentView(5001L, 10001L, 101L, 1700, "UNPAID"));

    SubmitOrderResponse response = orderService.submit(101L,
        new SubmitOrderRequest("order-test-1", 10L, 20L, null,
            List.of(new OrderSkuItem(1L, 2)), 7001L, "test"));

    assertThat(response.mode()).isEqualTo("ORDER_CREATED");
    assertThat(response.status()).isEqualTo("PENDING_PAYMENT");

    OrderView created = orderService.get(response.orderId());
    assertThat(created.amountCent()).isEqualTo(1700);
    assertThat(created.items()).hasSize(1);
    assertThat(created.contactName()).isEqualTo("Test User");
    assertThat(created.deliveryAddress()).isEqualTo("Test Road 1");
    assertThat(orderService.events())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.eventKey()).isEqualTo("order:OrderCreated:" + response.orderId() + ":1");
          assertThat(event.eventType()).isEqualTo("OrderCreated");
          assertThat(event.aggregateId()).isEqualTo(response.orderId());
          assertThat(event.status()).isEqualTo("NEW");
          assertThat(event.payloadJson()).contains("\"status\":\"PENDING_PAYMENT\"");
        });

    orderService.markPaid(response.orderId());

    assertThat(orderService.get(response.orderId()).status()).isEqualTo("WAIT_MERCHANT_ACCEPT");
    assertThat(orderService.adminOrders(new AdminOrderQuery(10L, 101L, "WAIT_MERCHANT_ACCEPT", null, null, 1, 20))
        .items()).extracting("orderId").contains(response.orderId());
    OrderStatisticsView statistics = orderService.adminStatistics(new AdminOrderQuery(10L, null, null, null, null, 1, 1));
    assertThat(statistics.totalCount()).isGreaterThanOrEqualTo(1);
    assertThat(statistics.waitingAcceptCount()).isGreaterThanOrEqualTo(1);
    assertThat(orderService.events())
        .extracting("eventType")
        .containsExactly("OrderCreated", "OrderPaid");
    assertThat(orderService.dispatchPendingEvents(10)).isEqualTo(2);
    assertThat(orderService.events())
        .extracting("status")
        .containsExactly("SENT", "SENT");
    verify(catalogClient).confirm(any());
    verify(promotionClient).confirm(any());
    verify(queueClient).bindOrder(eq(6001L), any());
  }

  @Test
  void recoversTimedOutConsumerRecord() {
    String eventKey = "payment:PaymentPaid:99901:1";
    String consumerGroup = "mealflow-order-payment-consumer-test";
    consumerRecordMapper.insertProcessing(consumerRecordMapper.maxRecordId() + 100, eventKey, consumerGroup,
        "PaymentPaid", "{\"orderId\":99901}", LocalDateTime.now().minusMinutes(10));

    int recovered = orderService.recoverTimedOutConsumerRecords();

    assertThat(recovered).isGreaterThanOrEqualTo(1);
    assertThat(consumerRecordMapper.findStatus(eventKey, consumerGroup)).isEqualTo("TIMEOUT");
  }

  @Test
  void replaysTimedOutPaymentConsumerRecordFromStoredPayload() {
    when(catalogClient.snapshots(eq(10L), anyList()))
        .thenReturn(List.of(new OrderItemSnapshot(1L, "重放盖饭", 1000, 1)));
    when(catalogClient.reserve(any()))
        .thenReturn(new CatalogClient.ReserveStockResponse(List.of(8101L), "RESERVED"));
    when(promotionClient.lock(any()))
        .thenReturn(new PromotionClient.VoucherLockResponse(7101L, "LOCKED", 0));
    when(queueClient.apply(any()))
        .thenReturn(new QueueClient.QueueApplyResponse("READY", 6101L, null, null, 0, 0, null));
    when(paymentClient.create(any()))
        .thenReturn(new PaymentClient.PaymentView(5101L, 10101L, 101L, 1000, "UNPAID"));

    SubmitOrderResponse response = orderService.submit(101L,
        new SubmitOrderRequest("order-replay-1", 10L, 20L, null,
            List.of(new OrderSkuItem(1L, 1)), null, "replay"));
    String eventKey = "payment:PaymentPaid:" + response.payOrderId() + ":1";
    String consumerGroup = "mealflow-order-payment-consumer-replay";
    String payload = "{\"orderId\":" + response.orderId() + "}";
    consumerRecordMapper.insertProcessing(consumerRecordMapper.maxRecordId() + 300, eventKey, consumerGroup,
        "PaymentPaid", payload, LocalDateTime.now().minusMinutes(10));

    Boolean replayed = orderService.replayPaymentConsumerRecord(eventKey, consumerGroup);

    assertThat(replayed).isTrue();
    assertThat(orderService.get(response.orderId()).status()).isEqualTo("WAIT_MERCHANT_ACCEPT");
    assertThat(consumerRecordMapper.findByEvent(eventKey, consumerGroup))
        .satisfies(record -> {
          assertThat(record.getStatus()).isEqualTo("SUCCESS");
          assertThat(record.getEventType()).isEqualTo("PaymentPaid");
          assertThat(record.getPayloadJson()).isEqualTo(payload);
        });
  }

  @Test
  void resumesPaymentSagaAfterACompletedRemoteStep() {
    when(catalogClient.snapshots(eq(10L), anyList()))
        .thenReturn(List.of(new OrderItemSnapshot(1L, "恢复盖饭", 1200, 1)));
    when(catalogClient.reserve(any()))
        .thenReturn(new CatalogClient.ReserveStockResponse(List.of(8201L), "RESERVED"));
    when(promotionClient.lock(any()))
        .thenReturn(new PromotionClient.VoucherLockResponse(7201L, "LOCKED", 0));
    when(queueClient.apply(any()))
        .thenReturn(new QueueClient.QueueApplyResponse("READY", 6201L, null, null, 0, 0, null));
    when(paymentClient.create(any()))
        .thenReturn(new PaymentClient.PaymentView(5201L, 10201L, 101L, 1200, "UNPAID"));
    doThrow(new IllegalStateException("catalog unavailable")).doNothing().when(catalogClient).confirm(any());

    SubmitOrderResponse response = orderService.submit(101L,
        new SubmitOrderRequest("order-saga-retry", 10L, 20L, null,
            List.of(new OrderSkuItem(1L, 1)), null, "retry"));

    orderService.markPaid(response.orderId());

    assertThat(orderService.get(response.orderId()).status()).isEqualTo("PENDING_PAYMENT");
    assertThat(orderSagaMapper.findByOrderId(response.orderId()))
        .extracting("status").containsExactly("FAILED", "NEW", "NEW");

    orderSagaMapper.retryNow(response.orderId(), LocalDateTime.now());
    orderSagaCoordinator.processReadyForOrder(response.orderId());

    assertThat(orderService.get(response.orderId()).status()).isEqualTo("WAIT_MERCHANT_ACCEPT");
    assertThat(orderSagaMapper.findByOrderId(response.orderId()))
        .extracting("status").containsOnly("SUCCESS");
  }
}
