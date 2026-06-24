package com.ig.PostService.service;

import com.ig.PostService.exception.AccountLockedException;
import com.ig.PostService.exception.PostViolationException;
import com.ig.PostService.model.PostViolation;
import com.ig.PostService.repo.PostViolationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostViolationService {
    private final PostViolationRepo postViolationRepo;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {PostViolationException.class, AccountLockedException.class}
    )
    public void trackViolation(String userId, String violationReason, Runnable lockAccountAction) {
        PostViolation violation = new PostViolation();
        violation.setUserId(userId);
        violation.setReason(truncateReason(violationReason));
        violation.setCreatedAt(LocalDateTime.now());
        postViolationRepo.saveAndFlush(violation);

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long count = postViolationRepo.countByUserIdAndCreatedAtAfter(userId, since);

        if (count >= 3) {
            lockAccountAction.run();
            throw new AccountLockedException(
                    "Tài khoản bị khóa do đăng bài vi phạm chính sách " + count + " lần trong 24h");
        }
        throw new PostViolationException(violationReason + " (lần " + count + "/3 trong 24h)");
    }

    private String truncateReason(String reason) {
        if (reason == null) return null;
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}
