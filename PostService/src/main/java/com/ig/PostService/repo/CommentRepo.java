package com.ig.PostService.repo;

import com.ig.PostService.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentRepo extends JpaRepository<Comment, Long> {
    Optional<Comment> findByIdAndPost_Id(Long id, String postId);
}
