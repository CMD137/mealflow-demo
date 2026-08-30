package com.mealflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.mapper.LocalEventMapper;
import com.mealflow.payment.provider.AlipaySandboxAdapter;
import com.mealflow.payment.provider.PaymentProviderPort;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "mealflow.outbox.scheduler-enabled=false",
        "mealflow.payment.provider=alipay-sandbox"
    }
)
class PaymentPersistenceTest {
  @Autowired
  private PaymentService paymentService;

  @Autowired
  private LocalEventMapper localEventMapper;

  @MockBean(answer = Answers.CALLS_REAL_METHODS)
  private AlipaySandboxAdapter alipaySandboxAdapter;

  @BeforeEach
  void mockAlipayRefund() {
    doReturn(new PaymentProviderPort.RefundResult(true, false, "ALIPAY-TEST-TRADE",
        "ALIPAY-TEST-REFUND", "success", "{}"))
        .when(alipaySandboxAdapter).refund(anyString(), anyString(), anyInt());
  }

  @Test
  void createsAndPaysOrderInDatabase() {
    PaymentView created = paymentService.create(new CreatePaymentRequest("payment-test-1", 2001L, 101L, 3200));

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
    PaymentView created = paymentService.create(new CreatePaymentRequest("payment-test-stale-sending", 2002L, 101L, 1800));
    paymentService.mockPay(created.payOrderId());
    String eventKey = "payment:PaymentPaid:" + created.payOrderId() + ":1";
    long eventId = paymentService.events().stream()
        .filter(event -> event.eventKey().equals(eventKey))
        .findFirst()
        .orElseThrow()
        .id();
    localEventMapper.markSending(eventId, LocalDateTime.now(), LocalDateTime.now().minusMinutes(1));

    int sent = paymentService.dispatchPendingEvents(10);

    assertThat(sent).isGreaterThanOrEqualTo(1);
    assertThat(paymentService.events().stream().filter(event -> event.eventKey().equals(eventKey)).toList())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.status()).isEqualTo("SENT");
          assertThat(event.retryCount()).isEqualTo(2);
        });
  }

  @Test
  void callsProviderAndPersistsRefundResult() {
    PaymentView created = paymentService.create(new CreatePaymentRequest("payment-test-refund", 2003L, 101L, 990));
    paymentService.mockPay(created.payOrderId());

    PaymentView refunded = paymentService.refund(created.payOrderId());

    assertThat(refunded.status()).isEqualTo("REFUNDED");
    assertThat(paymentService.refund(created.payOrderId()).status()).isEqualTo("REFUNDED");
  }
}
