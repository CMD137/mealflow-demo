package com.mealflow.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.app.catalog.OrderSkuItem;
import com.mealflow.app.fulfillment.FulfillmentService;
import com.mealflow.app.order.OrderService;
import com.mealflow.app.order.OrderView;
import com.mealflow.app.order.SubmitOrderRequest;
import com.mealflow.app.order.SubmitOrderResponse;
import com.mealflow.app.payment.PaymentService;
import com.mealflow.app.promotion.PromotionService;
import com.mealflow.app.promotion.SeckillVoucherResponse;
import com.mealflow.app.queue.QueueService;
import com.mealflow.app.queue.QueueTicketView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MealFlowScenarioTest {
  @Autowired
  OrderService orderService;
  @Autowired
  PaymentService paymentService;
  @Autowired
  FulfillmentService fulfillmentService;
  @Autowired
  QueueService queueService;
  @Autowired
  PromotionService promotionService;

  @Test
  void shouldQueueAndCreateOrderAfterCapacityReleased() {
    queueService.setMerchantLimit(10L, 2);

    SubmitOrderRequest firstRequest = request("submit-dup-001");
    SubmitOrderResponse first = orderService.submit(101L, firstRequest);
    SubmitOrderResponse duplicate = orderService.submit(101L, firstRequest);
    assertThat(first.mode()).isEqualTo("ORDER_CREATED");
    assertThat(duplicate.orderId()).isEqualTo(first.orderId());

    SubmitOrderResponse second = orderService.submit(102L, request("submit-second-001"));
    assertThat(second.mode()).isEqualTo("ORDER_CREATED");

    SubmitOrderResponse queued = orderService.submit(103L, request("submit-queued-001"));
    assertThat(queued.mode()).isEqualTo("QUEUED");
    QueueTicketView waiting = queueService.getTicket(queued.ticketId());
    assertThat(waiting.status()).isEqualTo("WAITING");

    paymentService.mockPay(first.payOrderId());
    fulfillmentService.accept(first.orderId(), "accept-first-001");
    fulfillmentService.mealReady(first.orderId(), "meal-ready-first-001");

    QueueTicketView readyTicket = queueService.getTicket(queued.ticketId());
    assertThat(readyTicket.status()).isEqualTo("ORDER_CREATED");
    OrderView fromTicket = orderService.orders().stream()
        .filter(order -> queued.ticketId().equals(order.queueTicketId()))
        .findFirst()
        .orElseThrow();
    assertThat(fromTicket.status()).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void shouldProtectSeckillVoucherWithIdempotencyAndUniqueUserRule() {
    SeckillVoucherResponse first = promotionService.seckill(201L, 1000L, "claim-201-001");
    SeckillVoucherResponse duplicateRequest = promotionService.seckill(201L, 1000L, "claim-201-001");
    SeckillVoucherResponse duplicateUser = promotionService.seckill(201L, 1000L, "claim-201-002");

    assertThat(first.status()).isEqualTo("CLAIMED");
    assertThat(duplicateRequest.userVoucherId()).isEqualTo(first.userVoucherId());
    assertThat(duplicateUser.status()).isEqualTo("DUPLICATE");
  }

  private SubmitOrderRequest request(String requestId) {
    return new SubmitOrderRequest(
        requestId,
        10L,
        20L,
        null,
        List.of(new OrderSkuItem(1L, 1)),
        null,
        "少辣"
    );
  }
}
