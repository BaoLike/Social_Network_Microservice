package com.ig.PostService.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CommentResponse {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Long id;
    private String userName;
    private String comment;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private LocalDateTime commentAt;
    private Long liked;
    private Long parentCommentId;
    private String replyToUsername;
}
