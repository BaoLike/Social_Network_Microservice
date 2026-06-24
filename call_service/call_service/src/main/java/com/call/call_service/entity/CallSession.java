package com.call.call_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "call_session")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallSession {
    @MongoId
    String id;

    String conversationId;
    String callerId;
    String calleeId;
    String callerName;
    String callerAvatar;

    @Builder.Default
    CallType type = CallType.VOICE;

    CallStatus status;

    Instant createdAt;
    Instant answeredAt;
    Instant endedAt;
    Long durationSeconds;
    String endReason;
}
