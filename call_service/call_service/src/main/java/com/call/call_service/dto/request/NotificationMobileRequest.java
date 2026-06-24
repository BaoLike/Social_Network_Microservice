package com.call.call_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationMobileRequest {
    String userId;
    String tittle;
    String body;
    String notificationType;
    String callId;
    String callerId;
    String callerAvatar;
    String conversationId;
    String userNameSendMessage;
}
