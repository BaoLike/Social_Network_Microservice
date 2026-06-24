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
@Document(collection = "call_web_socket_session")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallWebSocketSession {
    @MongoId
    String id;

    String socketSessionId;
    String userId;
    Instant createdAt;
}
