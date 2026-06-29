package com.mealflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mealflow.order.api.OrderItemSnapshot;
import com.mealflow.order.api.OrderSkuItem;
import com.mealflow.order.api.OrderView;
import com.mealflow.order.api.SubmitOrderRequest;
import com.mealflow.order.api.SubmitOrderResponse;
import com.mealflow.order.client.CatalogClient;
import com.mealflow.order.client.PaymentClient;
import com.mealflow.order.client.PromotionClient;
import com.mealflow.order.client.QueueClient;
import com.mealflow.order.mapper.ConsumerRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
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

  @MockBean
  private CatalogClient catalogClient;

  @MockBean
  private PromotionClient promotionClient;

  @MockBean
  private QueueClient queueClient;

  @MockBean
  private PaymentClient paymentClient;

  @Test
  void createsAndUpdatesOrderInDatabase() {
    when(catalogClient.snapshots(eq(10L), anyList()))
        .thenReturn(List.of(new OrderItemSnapshot(1L, "Test Rice", 1000, 2)));
    when(catalogClient.reserve(any()))
        .thenReturn(new CatalogClient.ReserveStockResponse(List.of(8001L), "RESERVED"));
    when(promotionClient.lock(any()))
        .thenReturn(new PromotionClient.VoucherLockResponse(7001L, "LOCKED", 300));
    when(queueClient.apply(any()))
        .thenReturn(new QueueClient.QueueApplyResponse("READY", 6001L, null, null, 0, 0, null));
    when(paymentClient.create(any()))
        .thenReturn(new PaymentClient.PaymentView(5001L, 10001L, 1700, "UNPAID"));

    SubmitOrderResponse response = orderService.submit(101L,
        new SubmitOrderRequest("order-test-1", 10L, 20L, null,
            List.of(new OrderSkuItem(1L, 2)), 7001L, "test"));

    assertThat(response.mode()).isEqualTo("ORDER_CREATED");
    assertThat(response.status()).isEqualTo("PENDING_PAYMENT");

    OrderView created = orderService.get(response.orderId());
    assertThat(created.amountCent()).isEqualTo(1700);
    assertThat(created.items()).hasSize(1);
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
        LocalDateTime.now().minusMinutes(10));

    int recovered = orderService.recoverTimedOutConsumerRecords();

    assertThat(recovered).isGreaterThanOrEqualTo(1);
    assertThat(consumerRecordMapper.findStatus(eventKey, consumerGroup)).isEqualTo("TIMEOUT");
  }
}
