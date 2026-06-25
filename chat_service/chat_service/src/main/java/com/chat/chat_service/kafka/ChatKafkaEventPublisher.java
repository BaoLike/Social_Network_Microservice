package com.chat.chat_service.kafka;

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
@Slf4j(topic = "CHAT_KAFKA")
public class ChatKafkaEventPublisher {

    private static final String PRODUCER = "chat_service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishChatPush(String recipientUserId, String title, String body, String conversationId) {
        NotificationPushRequestedPayload payload = NotificationPushRequestedPayload.builder()
                .userId(recipientUserId)
                .title(title)
                .body(body)
                .notificationType("CHAT_MESSAGE")
                .conversationId(conversationId)
                .build();
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTypes.NOTIFICATION_PUSH_REQUESTED, PRODUCER, payload);
        try {
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_PUSH_REQUESTED, recipientUserId,
                    objectMapper.writeValueAsString(envelope));
            log.info("Published chat push for userId={}", recipientUserId);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish chat push: {}", e.getMessage());
        }
    }
}
