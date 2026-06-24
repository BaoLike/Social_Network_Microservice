package com.notification.notification_service.service.impl;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.notification.notification_service.dto.request.CreateInteractionNotificationRequest;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.response.AppNotificationResponse;
import com.notification.notification_service.entity.AppNotification;
import com.notification.notification_service.repository.AppNotificationRepository;
import com.notification.notification_service.service.IAppNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j(topic = "APP_NOTIFICATION_SERVICE")
public class AppNotificationService implements IAppNotificationService {

    private static final String TYPE_LIKE = "LIKE";
    private static final String TYPE_COMMENT = "COMMENT";

    AppNotificationRepository appNotificationRepository;
    MobileNotifyService mobileNotifyService;

    @Override
    public void createInteractionNotification(CreateInteractionNotificationRequest request) {
        if (request.getRecipientUserId() == null || request.getActorUserId() == null) {
            return;
        }
        if (request.getRecipientUserId().equals(request.getActorUserId())) {
            return;
        }

        String displayName = buildDisplayName(request.getActorFirstName(), request.getActorLastName());
        String message = buildMessage(displayName, request.getType());

        AppNotification notification = AppNotification.builder()
                .recipientUserId(request.getRecipientUserId())
                .actorUserId(request.getActorUserId())
                .actorFirstName(request.getActorFirstName())
                .actorLastName(request.getActorLastName())
                .type(request.getType())
                .postId(request.getPostId())
                .message(message)
                .createdAt(Instant.now())
                .read(false)
                .build();

        appNotificationRepository.save(notification);

        try {
            mobileNotifyService.sendMobileNotification(
                    NotificationMobileRequest.builder()
                            .userId(request.getRecipientUserId())
                            .tittle("Thông báo")
                            .body(message)
                            .postId(request.getPostId())
                            .notificationType(request.getType())
                            .build()
            );
        } catch (FirebaseMessagingException e) {
            log.warn("Push notification failed for userId={}: {}", request.getRecipientUserId(), e.getMessage());
        }
    }

    @Override
    public List<AppNotificationResponse> getNotificationsForUser(String userId) {
        return appNotificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AppNotificationResponse toResponse(AppNotification notification) {
        return AppNotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .postId(notification.getPostId())
                .message(notification.getMessage())
                .actorFirstName(notification.getActorFirstName())
                .actorLastName(notification.getActorLastName())
                .createdAt(notification.getCreatedAt())
                .read(notification.isRead())
                .build();
    }

    private String buildDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "Người dùng" : full;
    }

    private String buildMessage(String displayName, String type) {
        if (TYPE_COMMENT.equalsIgnoreCase(type)) {
            return displayName + " đã bình luận bài viết của bạn";
        }
        return displayName + " đã thích bài viết của bạn";
    }
}
