package com.identity_service.identity.repository;

import com.identity_service.identity.model.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findFirstByUsers_UserIdOrderByExpiredAtDesc(String userId);

    void deleteByUsers_UserId(String userId);
}
