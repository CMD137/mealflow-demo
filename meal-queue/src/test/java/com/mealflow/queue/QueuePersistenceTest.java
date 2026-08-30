package com.mealflow.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        "test",
        1L,
        10L,
        "Queue User",
        "13800000001",
        "Queue Road 1"
    );
    QueueApplyResponse first = queueService.apply(new QueueApplyRequest("queue-test-1", 1L, 10L, snapshot,
        LocalDateTime.now().plusMinutes(10), 0, 1));
    QueueApplyResponse second = queueService.apply(new QueueApplyRequest("queue-test-2", 2L, 10L, snapshot,
        LocalDateTime.now().plusMinutes(10), 0, 1));

    assertThat(first.result()).isEqualTo("READY");
    assertThat(second.result()).isEqualTo("QUEUED");

    ReleaseCapacityResponse release = queueService.releaseCapacity(first.capacityTokenId(), "TEST_RELEASE");

    assertThat(release.released()).isTrue();
    assertThat(release.readyTicket()).isNotNull();
    assertThat(release.readyTicket().ticketId()).isEqualTo(second.ticketId());
    assertThat(queueService.getTicket(second.ticketId()).status()).isEqualTo("READY");
    assertThatThrownBy(() -> queueService.getTicket(second.ticketId(), 1L))
        .hasMessageContaining("does not belong to current user");
    assertThat(queueService.getTicket(second.ticketId(), 2L).ticketId()).isEqualTo(second.ticketId());
    assertThat(queueService.metrics(10L)).containsEntry("held", 1);
    assertThat(queueService.recoverableTickets(10))
        .singleElement()
        .satisfies(ticket -> {
          assertThat(ticket.ticketId()).isEqualTo(second.ticketId());
          assertThat(ticket.capacityTokenId()).isEqualTo(release.readyTicket().capacityTokenId());
        });

    ReleaseCapacityResponse duplicateRelease = queueService.releaseCapacity(first.capacityTokenId(), "TEST_RELEASE_AGAIN");

    assertThat(duplicateRelease.released()).isFalse();
    assertThat(duplicateRelease.readyTicket()).isNotNull();
    assertThat(duplicateRelease.readyTicket().ticketId()).isEqualTo(second.ticketId());
    assertThat(duplicateRelease.readyTicket().capacityTokenId()).isEqualTo(release.readyTicket().capacityTokenId());
    assertThat(queueService.metrics(10L)).containsEntry("held", 1);
  }

  @Test
  void persistsMerchantQueueLimit() {
    queueService.setMerchantLimit(10L, 3);

    assertThat(queueService.metrics(10L)).containsEntry("limit", 3);

    queueService.setMerchantLimit(10L, 0);

    assertThat(queueService.metrics(10L)).containsEntry("limit", 1);
  }
}
