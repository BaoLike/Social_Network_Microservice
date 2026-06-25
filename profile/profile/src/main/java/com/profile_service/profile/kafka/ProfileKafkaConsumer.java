package com.profile_service.profile.kafka;

import com.common_library.common.kafka.EventEnvelope;
import com.common_library.common.kafka.EventTypes;
import com.common_library.common.kafka.KafkaTopics;
import com.common_library.common.kafka.payload.UserEmailVerifiedPayload;
import com.common_library.common.kafka.payload.UserRegisteredPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.repository.UserProfileRepository;
import com.profile_service.profile.service.IUserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j(topic = "PROFILE_KAFKA")
public class ProfileKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final IUserProfileService userProfileService;
    private final UserProfileRepository userProfileRepository;

    @KafkaListener(topics = KafkaTopics.USER_REGISTERED, groupId = "profile-service")
    public void onUserRegistered(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.USER_REGISTERED.equals(envelope.getEventType())) {
                return;
            }
            UserRegisteredPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), UserRegisteredPayload.class);
            if (payload.getUserId() == null) {
                return;
            }
            if (userProfileRepository.findByUserId(payload.getUserId()).isPresent()) {
                log.info("Profile already exists for userId={}", payload.getUserId());
                return;
            }
            ProfileCreationRequest request = ProfileCreationRequest.builder()
                    .userId(payload.getUserId())
                    .userName(payload.getUsername())
                    .avatar(payload.getAvatar())
                    .firstName(payload.getFirstName())
                    .lastName(payload.getLastName())
                    .gender(payload.getGender())
                    .dob(parseDob(payload.getDob()))
                    .address(payload.getAddress())
                    .phone(payload.getPhone())
                    .build();
            userProfileService.createProfile(request);
            log.info("Created profile from Kafka for userId={}", payload.getUserId());
        } catch (Exception e) {
            log.error("Failed to consume user.registered: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.USER_EMAIL_VERIFIED, groupId = "profile-service")
    public void onUserEmailVerified(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.USER_EMAIL_VERIFIED.equals(envelope.getEventType())) {
                return;
            }
            UserEmailVerifiedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), UserEmailVerifiedPayload.class);
            log.info("User email verified (profile ack) userId={}", payload.getUserId());
        } catch (Exception e) {
            log.error("Failed to consume user.email.verified: {}", e.getMessage(), e);
        }
    }

    private LocalDate parseDob(String dob) {
        if (dob == null || dob.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dob);
        } catch (Exception e) {
            return null;
        }
    }
}
