package com.chat.chat_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_message")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessage {
    @MongoId
    String id;

    @Indexed
    String conversationId;

    String message;

    String iv;

    boolean encrypted;

    boolean e2e;

    ParticipantInfo sender;

    @Indexed
    Instant createdDate;

    boolean recalled;

    String recalledByLastName;

    Instant recalledAt;

    String storyReplyMediaUrl;

    String storyReplyOwnerId;
}
