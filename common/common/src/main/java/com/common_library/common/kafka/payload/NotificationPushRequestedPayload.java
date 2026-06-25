package com.common_library.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushRequestedPayload {
    private String userId;
    private String title;
    private String body;
    private String notificationType;
    private String conversationId;
    private String callId;
    private String callerId;
    private String callerAvatar;
    private String callerName;
    private String postId;
}
