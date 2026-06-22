package com.ig.PostService.repo;

import com.ig.PostService.model.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface CommentLikeRepo extends JpaRepository<CommentLike, Long> {
    boolean existsByCommentIdAndUserId(Long commentId, String userId);

    @Modifying
    @Transactional
    void deleteByCommentIdAndUserId(Long commentId, String userId);
}
