package com.common_library.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLikedPayload {
    private String postId;
    private String postOwnerId;
    private String actorId;
    private String likedAt;
}
