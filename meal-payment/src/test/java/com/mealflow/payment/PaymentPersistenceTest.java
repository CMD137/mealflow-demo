package com.mealflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class PaymentPersistenceTest {
  @Autowired
  private PaymentService paymentService;

  @Test
  void createsAndPaysOrderInDatabase() {
    PaymentView created = paymentService.create(new CreatePaymentRequest("payment-test-1", 2001L, 3200));

    assertThat(created.status()).isEqualTo("UNPAID");

    PaymentView paid = paymentService.mockPay(created.payOrderId());

    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(paymentService.get(created.payOrderId()).status()).isEqualTo("PAID");
    assertThat(paymentService.events())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.eventKey()).isEqualTo("payment:PaymentPaid:" + created.payOrderId() + ":1");
          assertThat(event.eventType()).isEqualTo("PaymentPaid");
          assertThat(event.aggregateId()).isEqualTo(created.payOrderId());
          assertThat(event.status()).isEqualTo("NEW");
          assertThat(event.payloadJson()).contains("\"orderId\":2001");
        });

    paymentService.mockPay(created.payOrderId());

    assertThat(paymentService.events()).hasSize(1);

    int sent = paymentService.dispatchPendingEvents(10);

    assertThat(sent).isEqualTo(1);
    assertThat(paymentService.events())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.status()).isEqualTo("SENT");
          assertThat(event.retryCount()).isEqualTo(1);
        });
  }
}
