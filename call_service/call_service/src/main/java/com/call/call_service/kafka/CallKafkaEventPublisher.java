package com.call.call_service.kafka;

import com.call.call_service.entity.CallSession;
import com.common_library.common.kafka.EventEnvelope;
import com.common_library.common.kafka.EventEnvelopeFactory;
import com.common_library.common.kafka.EventTypes;
import com.common_library.common.kafka.KafkaTopics;
import com.common_library.common.kafka.payload.NotificationPushRequestedPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j(topic = "CALL_KAFKA")
public class CallKafkaEventPublisher {

    private static final String PRODUCER = "call_service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishIncomingCall(CallSession session, String callerLabel) {
        NotificationPushRequestedPayload payload = NotificationPushRequestedPayload.builder()
                .userId(session.getCalleeId())
                .title("Cuộc gọi đến")
                .body(callerLabel + " đang gọi cho bạn")
                .notificationType("INCOMING_CALL")
                .callId(session.getId())
                .callerId(session.getCallerId())
                .callerAvatar(session.getCallerAvatar())
                .callerName(callerLabel)
                .conversationId(session.getConversationId())
                .build();
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTypes.NOTIFICATION_PUSH_REQUESTED, PRODUCER, payload);
        try {
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_PUSH_REQUESTED, session.getCalleeId(),
                    objectMapper.writeValueAsString(envelope));
            log.info("Published incoming-call push userId={} callId={}",
                    session.getCalleeId(), session.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to publish incoming-call push: {}", e.getMessage());
        }
    }
}
