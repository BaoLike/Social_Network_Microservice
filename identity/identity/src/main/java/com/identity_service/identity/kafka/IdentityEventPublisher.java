package com.identity_service.identity.kafka;

import com.common_library.common.kafka.EventEnvelope;
import com.common_library.common.kafka.EventEnvelopeFactory;
import com.common_library.common.kafka.EventTypes;
import com.common_library.common.kafka.KafkaTopics;
import com.common_library.common.kafka.payload.UserEmailVerifyRequestedPayload;
import com.common_library.common.kafka.payload.UserEmailVerifiedPayload;
import com.common_library.common.kafka.payload.UserRegisteredPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j(topic = "IDENTITY_KAFKA")
public class IdentityEventPublisher {

    private static final String PRODUCER = "identity";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserRegistered(User user, UserCreationRequest request) {
        UserRegisteredPayload payload = UserRegisteredPayload.builder()
                .userId(user.getUserId())
                .username(user.getUserName())
                .email(user.getEmail())
                .registeredAt(Instant.now().toString())
                .avatar(request.getAvatar())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .gender(request.getGender())
                .dob(request.getDob() != null ? request.getDob().toString() : null)
                .address(request.getAddress())
                .phone(request.getPhone())
                .build();
        publish(KafkaTopics.USER_REGISTERED, user.getUserId(),
                EventTypes.USER_REGISTERED, payload);
    }

    public void publishEmailVerifyRequested(User user, String otp) {
        UserEmailVerifyRequestedPayload payload = UserEmailVerifyRequestedPayload.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .username(user.getUserName())
                .otp(otp)
                .build();
        publish(KafkaTopics.USER_EMAIL_VERIFY_REQUESTED, user.getUserId(),
                EventTypes.USER_EMAIL_VERIFY_REQUESTED, payload);
    }

    public void publishEmailVerified(User user) {
        UserEmailVerifiedPayload payload = UserEmailVerifiedPayload.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .verifiedAt(Instant.now().toString())
                .build();
        publish(KafkaTopics.USER_EMAIL_VERIFIED, user.getUserId(),
                EventTypes.USER_EMAIL_VERIFIED, payload);
    }

    private void publish(String topic, String key, String eventType, Object payload) {
        EventEnvelope envelope = EventEnvelopeFactory.create(eventType, PRODUCER, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
            log.info("Published {} to topic={} key={}", eventType, topic, key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka event {}: {}", eventType, e.getMessage());
        }
    }
}
