package com.mealflow.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.queue.api.QueueApplyRequest;
import com.mealflow.queue.api.QueueApplyResponse;
import com.mealflow.queue.api.QueueTicketSnapshot;
import com.mealflow.queue.api.ReleaseCapacityResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class QueuePersistenceTest {
  @Autowired
  private QueueService queueService;

  @Test
  void persistsTicketAndPromotesItWhenCapacityIsReleased() {
    QueueTicketSnapshot snapshot = new QueueTicketSnapshot(
        List.of(Map.of("skuId", 101L, "quantity", 1)),
        List.of(9001L),
        null,
        2800,
        "test"
    );
    QueueApplyResponse first = queueService.apply(new QueueApplyRequest("queue-test-1", 1L, 10L, snapshot,
        LocalDateTime.now().plusMinutes(10), 0));
    QueueApplyResponse second = queueService.apply(new QueueApplyRequest("queue-test-2", 2L, 10L, snapshot,
        LocalDateTime.now().plusMinutes(10), 0));

    assertThat(first.result()).isEqualTo("READY");
    assertThat(second.result()).isEqualTo("QUEUED");

    ReleaseCapacityResponse release = queueService.releaseCapacity(first.capacityTokenId(), "TEST_RELEASE");

    assertThat(release.released()).isTrue();
    assertThat(release.readyTicket()).isNotNull();
    assertThat(release.readyTicket().ticketId()).isEqualTo(second.ticketId());
    assertThat(queueService.getTicket(second.ticketId()).status()).isEqualTo("READY");
  }
}
