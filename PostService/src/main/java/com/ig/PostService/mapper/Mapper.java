package com.ig.PostService.mapper;

import com.ig.PostService.model.Comment;
import com.ig.PostService.model.Post;
import com.ig.PostService.payload.response.CommentResponse;
import com.ig.PostService.payload.response.PostResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Mapper {
    public PostResponse PostResponseMapper(Post post){
        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setLikedByUser(false);
        response.setFirstName(post.getFirstName());
        response.setAvatarUrl(post.getAvatarUrl());
        response.setUserName(post.getUserName());
        response.setLastName(post.getLastName());
        response.setLiked(post.getLiked());
        response.setDescription(post.getDescription());
        response.setCreateAt(post.getCreateAt());
        response.setUrlMedia(post.getUrlMedia());
        response.setUnverifiedInfo(Boolean.TRUE.equals(post.getUnverifiedInfo()));
        response.setCommentList(buildThreadedCommentList(post));
        return response;
    }

    public List<CommentResponse> buildThreadedCommentList(Post post) {
        List<CommentResponse> result = new ArrayList<>();
        if (post.getCommentList() == null || post.getCommentList().isEmpty()) {
            return result;
        }

        List<Comment> topLevel = new ArrayList<>();
        Map<Long, List<Comment>> repliesByRoot = new HashMap<>();

        for (Comment comment : post.getCommentList()) {
            if (comment.getParentComment() == null) {
                topLevel.add(comment);
            } else {
                Long rootId = getRootCommentId(comment);
                repliesByRoot.computeIfAbsent(rootId, k -> new ArrayList<>()).add(comment);
            }
        }

        topLevel.sort(Comparator.comparing(Comment::getCommentAt, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Comment parent : topLevel) {
            result.add(CommentResponseMapper(parent));
            List<Comment> replies = repliesByRoot.getOrDefault(parent.getId(), new ArrayList<>());
            replies.sort(Comparator.comparing(Comment::getCommentAt, Comparator.nullsLast(Comparator.naturalOrder())));
            for (Comment reply : replies) {
                CommentResponse replyResponse = CommentResponseMapper(reply);
                replyResponse.setParentCommentId(parent.getId());
                if (reply.getParentComment() != null) {
                    replyResponse.setReplyToUsername(reply.getParentComment().getUserName());
                }
                result.add(replyResponse);
            }
        }
        return result;
    }

    private Long getRootCommentId(Comment comment) {
        Comment current = comment;
        while (current.getParentComment() != null) {
            current = current.getParentComment();
        }
        return current.getId();
    }

    public CommentResponse CommentResponseMapper(Comment comment){
        CommentResponse commentResponse = new CommentResponse();
        commentResponse.setId(comment.getId());
        commentResponse.setUserName(comment.getUserName());
        commentResponse.setFirstName(comment.getFirstName());
        commentResponse.setAvatarUrl(comment.getAvatarUrl());
        commentResponse.setLastName(comment.getLastName());
        commentResponse.setComment(comment.getComment());
        commentResponse.setCommentAt(comment.getCommentAt());
        commentResponse.setLiked(comment.getLiked());
        if (comment.getParentComment() != null) {
            commentResponse.setParentCommentId(comment.getParentComment().getId());
            commentResponse.setReplyToUsername(comment.getParentComment().getUserName());
        }
        return commentResponse;
    }
}
