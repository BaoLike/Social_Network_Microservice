package com.common_library.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationInteractionCreatedPayload {
    private String recipientUserId;
    private String actorUserId;
    private String actorFirstName;
    private String actorLastName;
    private String type;
    private String postId;
}
