package com.ig.PostService.kafka;

import com.common_library.common.kafka.EventEnvelope;
import com.common_library.common.kafka.EventEnvelopeFactory;
import com.common_library.common.kafka.EventTypes;
import com.common_library.common.kafka.KafkaTopics;
import com.common_library.common.kafka.payload.NotificationInteractionCreatedPayload;
import com.common_library.common.kafka.payload.PostCommentedPayload;
import com.common_library.common.kafka.payload.PostLikedPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PostKafkaEventPublisher {

    private static final String PRODUCER = "PostService";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishInteraction(NotificationInteractionCreatedPayload payload) {
        publish(KafkaTopics.NOTIFICATION_INTERACTION_CREATED, payload.getRecipientUserId(),
                EventTypes.NOTIFICATION_INTERACTION_CREATED, payload);
    }

    public void publishPostLiked(PostLikedPayload payload) {
        publish(KafkaTopics.POST_LIKED, payload.getPostId(),
                EventTypes.POST_LIKED, payload);
    }

    public void publishPostCommented(PostCommentedPayload payload) {
        publish(KafkaTopics.POST_COMMENTED, payload.getPostId(),
                EventTypes.POST_COMMENTED, payload);
    }

    private void publish(String topic, String key, String eventType, Object payload) {
        EventEnvelope envelope = EventEnvelopeFactory.create(eventType, PRODUCER, payload);
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(envelope));
            log.info("Published {} topic={} key={}", eventType, topic, key);
        } catch (JsonProcessingException e) {
            log.error("Kafka serialize failed for {}: {}", eventType, e.getMessage());
        }
    }
}
