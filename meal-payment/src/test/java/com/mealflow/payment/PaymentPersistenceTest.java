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
  }
}
