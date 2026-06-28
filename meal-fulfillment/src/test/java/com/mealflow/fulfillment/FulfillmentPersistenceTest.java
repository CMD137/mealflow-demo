package com.mealflow.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mealflow.fulfillment.api.OrderView;
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

    OrderView order = fulfillmentService.accept(20001L, "fulfillment-test-accept");

    assertThat(order.orderId()).isEqualTo(20001L);
    assertThat(fulfillmentService.operations())
        .anySatisfy(operation -> {
          assertThat(operation.requestId()).isEqualTo("fulfillment-test-accept");
          assertThat(operation.orderId()).isEqualTo(20001L);
          assertThat(operation.action()).isEqualTo("ACCEPT");
          assertThat(operation.status()).isEqualTo("SUCCESS");
        });
    assertThat(fulfillmentService.events())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.eventKey()).isEqualTo("fulfillment:FulfillmentAccepted:20001:1");
          assertThat(event.eventType()).isEqualTo("FulfillmentAccepted");
          assertThat(event.aggregateId()).isEqualTo(20001L);
          assertThat(event.status()).isEqualTo("NEW");
          assertThat(event.payloadJson()).contains("\"requestId\":\"fulfillment-test-accept\"");
        });

    assertThat(fulfillmentService.dispatchPendingEvents(10)).isEqualTo(1);
    assertThat(fulfillmentService.events())
        .singleElement()
        .satisfies(event -> {
          assertThat(event.status()).isEqualTo("SENT");
          assertThat(event.retryCount()).isEqualTo(1);
        });
    server.verify();
  }
}
