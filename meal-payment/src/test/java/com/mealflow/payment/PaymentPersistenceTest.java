package com.mealflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.mapper.LocalEventMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "mealflow.outbox.scheduler-enabled=false"
    }
)
class PaymentPersistenceTest {
  @Autowired
  private PaymentService paymentService;

  @Autowired
  private LocalEventMapper localEventMapper;

  @Test
  void createsAndPaysOrderInDatabase() {
    PaymentView created = paymentService.create(new CreatePaymentRequest("payment-test-1", 2001L, 3200));

    assertThat(created.status()).isEqualTo("UNPAID");

    PaymentView paid = paymentService.mockPay(created.payOrderId());

    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(paymentService.get(created.payOrderId()).status()).isEqualTo("PAID");
    String eventKey = "payment:PaymentPaid:" + created.payOrderId() + ":1";
    assertThat(paymentService.events().stream().filter(event -> event.eventKey().equals(eventKey)).toList())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.eventType()).isEqualTo("PaymentPaid");
          assertThat(event.aggregateId()).isEqualTo(created.payOrderId());
          assertThat(event.status()).isEqualTo("NEW");
          assertThat(event.payloadJson()).contains("\"orderId\":2001");
        });

    paymentService.mockPay(created.payOrderId());

    assertThat(paymentService.events().stream().filter(event -> event.eventKey().equals(eventKey)).toList()).hasSize(1);

    int sent = paymentService.dispatchPendingEvents(10);

    assertThat(sent).isGreaterThanOrEqualTo(1);
    assertThat(paymentService.events().stream().filter(event -> event.eventKey().equals(eventKey)).toList())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.status()).isEqualTo("SENT");
          assertThat(event.retryCount()).isEqualTo(1);
        });
  }

  @Test
  void recoversStaleSendingOutboxEvent() {
    PaymentView created = paymentService.create(new CreatePaymentRequest("payment-test-stale-sending", 2002L, 1800));
    paymentService.mockPay(created.payOrderId());
    String eventKey = "payment:PaymentPaid:" + created.payOrderId() + ":1";
    long eventId = paymentService.events().stream()
        .filter(event -> event.eventKey().equals(eventKey))
        .findFirst()
        .orElseThrow()
        .id();
    localEventMapper.markSending(eventId, LocalDateTime.now().minusMinutes(2));

    int sent = paymentService.dispatchPendingEvents(10);

    assertThat(sent).isGreaterThanOrEqualTo(1);
    assertThat(paymentService.events().stream().filter(event -> event.eventKey().equals(eventKey)).toList())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.status()).isEqualTo("SENT");
          assertThat(event.retryCount()).isEqualTo(2);
        });
  }
}
