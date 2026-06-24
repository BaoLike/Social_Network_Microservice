package com.call.call_service.service.impl;

import com.call.call_service.dto.request.IntrospectRequest;
import com.call.call_service.dto.response.IntrospectResponse;
import com.call.call_service.repository.httpclient.IdentityClient;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IdentityService {
    IdentityClient identityClient;

    public IntrospectResponse introspect(IntrospectRequest request) {
        try {
            var result = identityClient.introspect(request).getResult();
            if (Objects.isNull(result)) {
                return IntrospectResponse.builder().valid(false).build();
            }
            if (result.getUserId() == null || result.getUserId().isBlank()) {
                String userId = extractUserIdFromToken(request.getToken());
                if (userId != null) {
                    result.setUserId(userId);
                }
            }
            return result;
        } catch (FeignException e) {
            log.error("Introspect failed: {}", e.getMessage());
            String userId = extractUserIdFromToken(request.getToken());
            if (userId != null) {
                return IntrospectResponse.builder().valid(true).userId(userId).build();
            }
            return IntrospectResponse.builder().valid(false).build();
        }
    }

    private String extractUserIdFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            var jwt = com.nimbusds.jwt.SignedJWT.parse(token.trim());
            var exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp != null && exp.before(new java.util.Date())) {
                return null;
            }
            return jwt.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
