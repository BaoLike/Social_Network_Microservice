package com.ig.PostService.repo;

import com.ig.PostService.model.PostViolation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface PostViolationRepo extends JpaRepository<PostViolation, Long> {
    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime since);
}
