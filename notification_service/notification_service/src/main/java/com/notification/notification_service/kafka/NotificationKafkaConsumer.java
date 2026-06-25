package com.notification.notification_service.kafka;

import com.common_library.common.kafka.EventEnvelope;
import com.common_library.common.kafka.EventTypes;
import com.common_library.common.kafka.KafkaTopics;
import com.common_library.common.kafka.payload.NotificationInteractionCreatedPayload;
import com.common_library.common.kafka.payload.NotificationPushRequestedPayload;
import com.common_library.common.kafka.payload.StoryCreatedPayload;
import com.common_library.common.kafka.payload.UserEmailVerifyRequestedPayload;
import com.common_library.common.kafka.payload.UserEmailVerifiedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.notification_service.dto.request.CreateInteractionNotificationRequest;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.service.IAppNotificationService;
import com.notification.notification_service.service.IMobileNotifyService;
import com.notification.notification_service.service.impl.OtpEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j(topic = "NOTIFICATION_KAFKA")
public class NotificationKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final IMobileNotifyService mobileNotifyService;
    private final IAppNotificationService appNotificationService;
    private final OtpEmailService otpEmailService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_PUSH_REQUESTED, groupId = "notification-service")
    public void onPushRequested(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.NOTIFICATION_PUSH_REQUESTED.equals(envelope.getEventType())) {
                return;
            }
            NotificationPushRequestedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), NotificationPushRequestedPayload.class);

            NotificationMobileRequest request = NotificationMobileRequest.builder()
                    .userId(payload.getUserId())
                    .tittle(payload.getTitle())
                    .body(payload.getBody())
                    .notificationType(payload.getNotificationType())
                    .conversationId(payload.getConversationId())
                    .callId(payload.getCallId())
                    .callerId(payload.getCallerId())
                    .callerAvatar(payload.getCallerAvatar())
                    .userNameSendMessage(payload.getCallerName())
                    .postId(payload.getPostId())
                    .build();
            mobileNotifyService.sendMobileNotification(request);
        } catch (Exception e) {
            log.error("Failed to consume notification.push.requested: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_INTERACTION_CREATED, groupId = "notification-service")
    public void onInteractionCreated(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.NOTIFICATION_INTERACTION_CREATED.equals(envelope.getEventType())) {
                return;
            }
            NotificationInteractionCreatedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), NotificationInteractionCreatedPayload.class);

            appNotificationService.createInteractionNotification(
                    CreateInteractionNotificationRequest.builder()
                            .recipientUserId(payload.getRecipientUserId())
                            .actorUserId(payload.getActorUserId())
                            .actorFirstName(payload.getActorFirstName())
                            .actorLastName(payload.getActorLastName())
                            .type(payload.getType())
                            .postId(payload.getPostId())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to consume notification.interaction.created: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.USER_EMAIL_VERIFY_REQUESTED, groupId = "notification-service")
    public void onEmailVerifyRequested(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.USER_EMAIL_VERIFY_REQUESTED.equals(envelope.getEventType())) {
                return;
            }
            UserEmailVerifyRequestedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), UserEmailVerifyRequestedPayload.class);
            otpEmailService.sendVerificationOtp(
                    payload.getEmail(),
                    payload.getUsername() != null ? payload.getUsername() : "User",
                    payload.getOtp()
            );
        } catch (Exception e) {
            log.error("Failed to consume user.email.verify.requested: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.USER_EMAIL_VERIFIED, groupId = "notification-service")
    public void onEmailVerified(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.USER_EMAIL_VERIFIED.equals(envelope.getEventType())) {
                return;
            }
            UserEmailVerifiedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), UserEmailVerifiedPayload.class);
            log.info("User email verified notification ack userId={}", payload.getUserId());
        } catch (Exception e) {
            log.error("Failed to consume user.email.verified: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.STORY_CREATED, groupId = "notification-service")
    public void onStoryCreated(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            if (!EventTypes.STORY_CREATED.equals(envelope.getEventType())) {
                return;
            }
            StoryCreatedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), StoryCreatedPayload.class);
            log.info("Story created event received storyId={} authorId={}",
                    payload.getStoryId(), payload.getAuthorId());
        } catch (Exception e) {
            log.error("Failed to consume story.created: {}", e.getMessage(), e);
        }
    }
}
