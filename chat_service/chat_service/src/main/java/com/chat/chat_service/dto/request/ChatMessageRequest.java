package com.chat.chat_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageRequest {
    @NotBlank
    String conversationId;

    @NotBlank
    String message;

    String iv;

    /** Plaintext preview for push notification only; not stored in DB. */
    String notificationPreview;

    String storyReplyMediaUrl;

    String storyReplyOwnerId;
}
