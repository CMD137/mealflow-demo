package com.mealflow.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.mealflow.fulfillment.api.OrderView;
import com.mealflow.fulfillment.mapper.MealReadyTaskMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.loadbalancer.enabled=false",
        "mealflow.outbox.scheduler-enabled=false",
        "mealflow.services.order=http://order-test",
        "mealflow.services.queue=http://queue-test"
    }
)
class FulfillmentPersistenceTest {
  @Autowired
  private FulfillmentService fulfillmentService;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private MealReadyTaskMapper mealReadyTaskMapper;

  @Test
  void persistsOperationLogWhenOrderIsAccepted() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("http://order-test/orders/20001/merchant-accept"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {
              "success": true,
              "code": "OK",
              "message": "success",
              "data": {
                "orderId": 20001,
                "userId": 101,
                "merchantId": 10,
                "status": "WAIT_MEAL_READY",
                "queueTicketId": null,
                "capacityTokenId": 30001,
                "payOrderId": 40001,
                "amountCent": 2800,
                "items": []
              }
            }
            """, MediaType.APPLICATION_JSON));

    OrderView order = fulfillmentService.accept(10L, 20001L, "fulfillment-test-accept");

    assertThat(order.orderId()).isEqualTo(20001L);
    assertThat(fulfillmentService.operations())
        .anySatisfy(operation -> {
          assertThat(operation.requestId()).isEqualTo("fulfillment-test-accept");
          assertThat(operation.orderId()).isEqualTo(20001L);
          assertThat(operation.action()).isEqualTo("ACCEPT");
          assertThat(operation.status()).isEqualTo("SUCCESS");
        });
    assertThat(fulfillmentService.events().stream()
        .filter(event -> event.eventKey().equals("fulfillment:FulfillmentAccepted:20001:1")).toList())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.eventKey()).isEqualTo("fulfillment:FulfillmentAccepted:20001:1");
          assertThat(event.eventType()).isEqualTo("FulfillmentAccepted");
          assertThat(event.aggregateId()).isEqualTo(20001L);
          assertThat(event.status()).isEqualTo("NEW");
          assertThat(event.payloadJson()).contains("\"requestId\":\"fulfillment-test-accept\"");
        });

    assertThat(fulfillmentService.dispatchPendingEvents(10)).isGreaterThanOrEqualTo(1);
    assertThat(fulfillmentService.events().stream()
        .filter(event -> event.eventKey().equals("fulfillment:FulfillmentAccepted:20001:1")).toList())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.status()).isEqualTo("SENT");
          assertThat(event.retryCount()).isEqualTo(1);
        });
    server.verify();
  }

  @Test
  void retriesTicketPromotionWithoutReleasingCapacityAgain() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("http://order-test/orders/20002/meal-ready"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(orderResponse(20002L, 30002L), MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://queue-test/queue/internal/capacity/30002/release"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {"success":true,"code":"OK","message":"success","data":{"released":true,
             "readyTicket":{"ticketId":50002,"ticketNo":"Q50002","capacityTokenId":60002,"snapshot":{}}}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://order-test/orders/internal/from-ticket/50002/60002"))
        .andExpect(method(HttpMethod.POST)).andRespond(withServerError());
    server.expect(requestTo("http://order-test/orders/internal/from-ticket/50002/60002"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"success\":true,\"code\":\"OK\",\"message\":\"success\"}",
            MediaType.APPLICATION_JSON));

    fulfillmentService.mealReady(10L, 20002L, "fulfillment-meal-ready-retry");

    assertThat(mealReadyTaskMapper.findByRequestId("fulfillment-meal-ready-retry"))
        .satisfies(task -> {
          assertThat(task.getStatus()).isEqualTo("FAILED");
          assertThat(task.isReleaseDone()).isTrue();
          assertThat(task.isPromoteDone()).isFalse();
        });

    mealReadyTaskMapper.retryNow("fulfillment-meal-ready-retry", LocalDateTime.now());
    fulfillmentService.dispatchMealReadyTasks(10);

    assertThat(mealReadyTaskMapper.findByRequestId("fulfillment-meal-ready-retry"))
        .satisfies(task -> {
          assertThat(task.getStatus()).isEqualTo("SUCCESS");
          assertThat(task.isReleaseDone()).isTrue();
          assertThat(task.isPromoteDone()).isTrue();
        });
    assertThat(fulfillmentService.operations()).anySatisfy(operation -> {
      assertThat(operation.requestId()).isEqualTo("fulfillment-meal-ready-retry");
      assertThat(operation.status()).isEqualTo("SUCCESS");
    });
    server.verify();
  }

  private String orderResponse(long orderId, long capacityTokenId) {
    return """
        {"success":true,"code":"OK","message":"success","data":{
          "orderId":%d,"userId":101,"merchantId":10,"status":"WAIT_RIDER_PICKUP",
          "queueTicketId":null,"capacityTokenId":%d,"payOrderId":40002,"amountCent":2800,"items":[]}}
        """.formatted(orderId, capacityTokenId);
  }
}
