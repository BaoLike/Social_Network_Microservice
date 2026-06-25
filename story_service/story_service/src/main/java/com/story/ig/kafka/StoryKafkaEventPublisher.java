package com.story.ig.kafka;

import com.common_library.common.kafka.EventEnvelope;
import com.common_library.common.kafka.EventEnvelopeFactory;
import com.common_library.common.kafka.EventTypes;
import com.common_library.common.kafka.KafkaTopics;
import com.common_library.common.kafka.payload.StoryCreatedPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class StoryKafkaEventPublisher {

    private static final String PRODUCER = "story_service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishStoryCreated(String authorId, String mediaUrl) {
        String storyId = UUID.randomUUID().toString();
        StoryCreatedPayload payload = StoryCreatedPayload.builder()
                .storyId(storyId)
                .authorId(authorId)
                .mediaUrl(mediaUrl)
                .createdAt(Instant.now().toString())
                .build();
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTypes.STORY_CREATED, PRODUCER, payload);
        try {
            kafkaTemplate.send(KafkaTopics.STORY_CREATED, storyId,
                    objectMapper.writeValueAsString(envelope));
            log.info("Published story.created storyId={} authorId={}", storyId, authorId);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish story.created: {}", e.getMessage());
        }
    }
}
