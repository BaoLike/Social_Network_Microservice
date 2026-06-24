package com.call.call_service.service.impl;

import com.call.call_service.entity.CallSession;
import com.call.call_service.entity.CallStatus;
import com.call.call_service.repository.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tự động đánh dấu MISSED và dọn activeCallByUser
 * cho các cuộc gọi bị bỏ quên (client crash / mất mạng / tắt app).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallTimeoutService {

    private static final long RING_TIMEOUT_SECONDS = 60L;    // RINGING quá 60s → MISSED
    private static final long ACTIVE_TIMEOUT_SECONDS = 3600L; // CONNECTED quá 1h → ENDED

    private final CallSessionRepository callSessionRepository;
    private final CallSessionService callSessionService;
    private final SignalingService signalingService;

    /** Chạy mỗi 30 giây */
    @Scheduled(fixedDelay = 30_000)
    public void cleanupStaleCalls() {
        cleanupStaleRinging();
        cleanupStaleActive();
    }

    private void cleanupStaleRinging() {
        Instant cutoff = Instant.now().minusSeconds(RING_TIMEOUT_SECONDS);
        List<CallSession> stale = callSessionRepository
                .findByStatusAndCreatedAtBefore(CallStatus.RINGING, cutoff);

        for (CallSession session : stale) {
            try {
                session.setStatus(CallStatus.MISSED);
                session.setEndedAt(Instant.now());
                session.setEndReason("NO_ANSWER");
                callSessionRepository.save(session);

                // Thông báo caller: không ai bắt máy
                signalingService.sendToUser(session.getCallerId(),
                        SignalingService.EVENT_CALL_ENDED,
                        Map.of("callId", session.getId(), "reason", "NO_ANSWER"));

                // Xóa khỏi in-memory map
                callSessionService.evictActiveCall(session.getCallerId(), session.getId());
                callSessionService.evictActiveCall(session.getCalleeId(), session.getId());

                log.info("[CallTimeout] MISSED callId={} caller={} callee={}",
                        session.getId(), session.getCallerId(), session.getCalleeId());
            } catch (Exception e) {
                log.error("[CallTimeout] Failed to mark MISSED callId={}", session.getId(), e);
            }
        }
    }

    private void cleanupStaleActive() {
        Instant cutoff = Instant.now().minusSeconds(ACTIVE_TIMEOUT_SECONDS);
        List<CallSession> stale = callSessionRepository
                .findByStatusAndCreatedAtBefore(CallStatus.CONNECTED, cutoff);

        for (CallSession session : stale) {
            try {
                session.setStatus(CallStatus.ENDED);
                session.setEndedAt(Instant.now());
                session.setEndReason("TIMEOUT");
                callSessionRepository.save(session);

                callSessionService.evictActiveCall(session.getCallerId(), session.getId());
                callSessionService.evictActiveCall(session.getCalleeId(), session.getId());

                log.info("[CallTimeout] ENDED (timeout 1h) callId={}", session.getId());
            } catch (Exception e) {
                log.error("[CallTimeout] Failed to end stale callId={}", session.getId(), e);
            }
        }
    }
}
