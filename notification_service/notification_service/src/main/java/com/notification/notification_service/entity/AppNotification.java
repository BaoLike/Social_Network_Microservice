package com.notification.notification_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "app_notification")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppNotification {
    @MongoId
    String id;

    @Indexed
    String recipientUserId;

    String actorUserId;
    String actorFirstName;
    String actorLastName;
    String type;
    String postId;
    String message;

    @Indexed
    Instant createdAt;

    boolean read;
}
