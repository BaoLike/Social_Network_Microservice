package com.notification.notification_service.service.impl;

import com.google.firebase.messaging.*;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.entity.UserDevices;
import com.notification.notification_service.repository.UserDeviceRepository;
import com.notification.notification_service.service.IMobileNotifyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Slf4j(topic = "MOBILE_NOTIFY_SERVICE")
public class MobileNotifyService implements IMobileNotifyService {
    UserDeviceRepository userDeviceRepository;

    @Override
    public void registerDevice(RegisterDeviceRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()
                || request.getTokenDevice() == null || request.getTokenDevice().isBlank()) {
            log.warn("Skip register device: missing userId or token");
            return;
        }

        // One FCM token belongs to one logged-in user; reassign when another account logs in on the same device.
        userDeviceRepository.deleteByDeviceToken(request.getTokenDevice());

        UserDevices device = UserDevices.builder()
                .userId(request.getUserId())
                .deviceToken(request.getTokenDevice())
                .build();

        userDeviceRepository.save(device);
        log.info("Register device thanh cong for userId={}", request.getUserId());
    }

    @Override
    public void sendMobileNotification(NotificationMobileRequest request) throws FirebaseMessagingException {

        List<UserDevices> userDevices
                = userDeviceRepository.findAllByUserId(request.getUserId());
        if (userDevices.isEmpty()) {
            log.warn("No registered devices found for userId={}", request.getUserId());
            return;
        }

        List<String> tokens = userDevices.stream().map(UserDevices::getDeviceToken)
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            log.warn("No valid device tokens for userId={}", request.getUserId());
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(request.getTittle())
                .setBody(request.getBody())
                .build();


        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification)
                .putData("recipientUserId", request.getUserId())
                .putData("postId", safeDataValue(request.getPostId()))
                .putData("notificationType", safeDataValue(request.getNotificationType()))
                .build();

        BatchResponse response =
                FirebaseMessaging.getInstance()
                        .sendEachForMulticast(message);

        log.info("Push notification result userId={}, success={}, failed={}",
                request.getUserId(), response.getSuccessCount(), response.getFailureCount());

        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size() && i < tokens.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }

            String token = tokens.get(i);
            FirebaseMessagingException exception = sendResponse.getException();
            String messageError = exception == null ? "unknown error" : exception.getMessage();
            MessagingErrorCode errorCode = exception == null ? null : exception.getMessagingErrorCode();

            log.warn("Push failed for userId={}, token={}, errorCode={}, message={}",
                    request.getUserId(), token, errorCode, messageError);

            if (shouldRemoveToken(errorCode, messageError)) {
                userDeviceRepository.deleteByUserIdAndDeviceToken(request.getUserId(), token);
                log.info("Removed invalid device token for userId={}, token={}", request.getUserId(), token);
            }
        }
    }

    private boolean shouldRemoveToken(MessagingErrorCode errorCode, String messageError) {
        if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return true;
        }
        if (messageError == null) {
            return false;
        }
        String normalized = messageError.toLowerCase(Locale.ROOT);
        return normalized.contains("registration token is not registered")
                || normalized.contains("unregistered");
    }

    private String safeDataValue(String value) {
        return value != null ? value : "";
    }

}