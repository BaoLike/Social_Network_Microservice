package com.notification.notification_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateInteractionNotificationRequest {
    String recipientUserId;
    String actorUserId;
    String actorFirstName;
    String actorLastName;
    String type;
    String postId;
}
