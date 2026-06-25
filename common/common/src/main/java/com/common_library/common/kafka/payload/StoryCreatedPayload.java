package com.common_library.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryCreatedPayload {
    private String storyId;
    private String authorId;
    private String mediaUrl;
    private String createdAt;
}
