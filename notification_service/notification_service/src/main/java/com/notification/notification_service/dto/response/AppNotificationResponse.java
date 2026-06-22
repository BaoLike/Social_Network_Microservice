package com.notification.notification_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppNotificationResponse {
    String id;
    String type;
    String postId;
    String message;
    String actorFirstName;
    String actorLastName;
    Instant createdAt;
    boolean read;
}
