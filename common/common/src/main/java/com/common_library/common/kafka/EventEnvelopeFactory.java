package com.common_library.common.kafka;

import java.time.Instant;
import java.util.UUID;

public final class EventEnvelopeFactory {

    private EventEnvelopeFactory() {}

    public static EventEnvelope create(String eventType, String producer, Object payload) {
        return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventVersion(1)
                .occurredAt(Instant.now().toString())
                .producer(producer)
                .payload(payload)
                .build();
    }

    public static EventEnvelope create(String eventType, String producer, String correlationId, Object payload) {
        EventEnvelope envelope = create(eventType, producer, payload);
        envelope.setCorrelationId(correlationId);
        return envelope;
    }
}
