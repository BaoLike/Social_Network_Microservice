package com.common_library.common.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {
    private String eventId;
    private String eventType;
    private int eventVersion;
    private String occurredAt;
    private String producer;
    private String correlationId;
    private Object payload;
}
