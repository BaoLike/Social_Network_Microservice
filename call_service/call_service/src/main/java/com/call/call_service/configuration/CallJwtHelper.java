package com.call.call_service.configuration;

import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * Extracts userId from JWT for call socket auth (fallback when introspect omits userId).
 */
@Slf4j
@Component
public class CallJwtHelper {

    public String extractUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(token.trim());
            Date expiry = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiry != null && expiry.before(Date.from(Instant.now()))) {
                return null;
            }
            String subject = signedJWT.getJWTClaimsSet().getSubject();
            return subject != null && !subject.isBlank() ? subject : null;
        } catch (Exception e) {
            log.debug("Cannot parse call socket token: {}", e.getMessage());
            return null;
        }
    }
}
