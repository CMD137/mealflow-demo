package com.mealflow.app.demo;

import com.mealflow.app.catalog.CatalogService;
import com.mealflow.app.order.OrderService;
import com.mealflow.app.payment.PaymentService;
import com.mealflow.app.promotion.PromotionService;
import com.mealflow.app.queue.QueueService;
import com.mealflow.common.api.Result;
import com.mealflow.infra.event.LocalEventStore;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoStateController {
  private final CatalogService catalogService;
  private final PromotionService promotionService;
  private final QueueService queueService;
  private final OrderService orderService;
  private final PaymentService paymentService;
  private final LocalEventStore localEventStore;

  public DemoStateController(CatalogService catalogService, PromotionService promotionService, QueueService queueService,
      OrderService orderService, PaymentService paymentService, LocalEventStore localEventStore) {
    this.catalogService = catalogService;
    this.promotionService = promotionService;
    this.queueService = queueService;
    this.orderService = orderService;
    this.paymentService = paymentService;
    this.localEventStore = localEventStore;
  }

  @GetMapping("/state")
  public Result<Map<String, Object>> state() {
    return Result.ok(Map.of(
        "skus", catalogService.listByMerchant(10),
        "stockReservations", catalogService.reservations(),
        "voucherClaims", promotionService.claims(),
        "voucherLocks", promotionService.locks(),
        "tickets", queueService.tickets(),
        "capacityTokens", queueService.tokens(),
        "orders", orderService.orders(),
        "payments", paymentService.payments(),
        "events", localEventStore.list()
    ));
  }
}
