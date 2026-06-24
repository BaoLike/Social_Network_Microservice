package com.call.call_service.service.impl;

import com.call.call_service.dto.request.NotificationMobileRequest;
import com.call.call_service.entity.CallSession;
import com.call.call_service.repository.httpclient.NotificationClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallPushNotificationService {

    private static final String TYPE_INCOMING_CALL = "INCOMING_CALL";

    NotificationClient notificationClient;
    SignalingService signalingService;

    public void notifyIncomingCallIfOffline(CallSession session) {
        if (session == null || session.getCalleeId() == null) {
            return;
        }
        if (signalingService.isUserOnline(session.getCalleeId())) {
            log.debug("Callee {} online on call socket — skip FCM", session.getCalleeId());
            return;
        }

        String callerLabel = session.getCallerName() != null && !session.getCallerName().isBlank()
                ? session.getCallerName()
                : "Ai đó";

        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId(session.getCalleeId())
                .tittle("Cuộc gọi đến")
                .body(callerLabel + " đang gọi cho bạn")
                .notificationType(TYPE_INCOMING_CALL)
                .callId(session.getId())
                .callerId(session.getCallerId())
                .callerAvatar(session.getCallerAvatar())
                .conversationId(session.getConversationId())
                .userNameSendMessage(callerLabel)
                .build();

        try {
            notificationClient.sendMobileNotification(request);
            log.info("Sent incoming-call FCM to userId={} callId={}", session.getCalleeId(), session.getId());
        } catch (Exception e) {
            log.warn("FCM incoming call failed for userId={}: {}", session.getCalleeId(), e.getMessage());
        }
    }
}
