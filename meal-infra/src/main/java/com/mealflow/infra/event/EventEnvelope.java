package com.mealflow.infra.event;

import java.time.LocalDateTime;
import java.util.Map;

public record EventEnvelope(
    long eventId,
    String eventKey,
    String eventType,
    int eventVersion,
    String aggregateType,
    long aggregateId,
    String traceId,
    LocalDateTime occurredAt,
    Map<String, Object> payload
) {
}
