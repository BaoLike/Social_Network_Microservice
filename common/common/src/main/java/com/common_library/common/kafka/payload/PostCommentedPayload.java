package com.common_library.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCommentedPayload {
    private String postId;
    private String postOwnerId;
    private String commentId;
    private String commenterId;
    private String commentPreview;
    private String commentedAt;
}
