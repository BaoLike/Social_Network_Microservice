package com.call.call_service.service.impl;

import com.call.call_service.configuration.WebRtcProperties;
import com.call.call_service.dto.request.InitiateCallRequest;
import com.call.call_service.dto.response.CallSessionResponse;
import com.call.call_service.dto.response.IceServerResponse;
import com.call.call_service.entity.CallSession;
import com.call.call_service.entity.CallStatus;
import com.call.call_service.entity.CallType;
import com.call.call_service.exception.AppException;
import com.call.call_service.exception.ErrorCode;
import com.call.call_service.repository.CallSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallSessionService {

    CallSessionRepository callSessionRepository;
    SignalingService signalingService;
    WebRtcProperties webRtcProperties;
    CallPushNotificationService callPushNotificationService;

    /** userId → callId */
    Map<String, String> activeCallByUser = new ConcurrentHashMap<>();

    public CallSessionResponse initiate(String callerId, InitiateCallRequest request) {
        if (callerId == null || callerId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (request.getCalleeId() == null || request.getCalleeId().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (callerId.equals(request.getCalleeId())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Không thể gọi chính mình");
        }

        if (activeCallByUser.containsKey(callerId)) {
            String staleId = activeCallByUser.get(callerId);
            // Auto-cleanup stale calls older than 90s (phone crash / no proper end)
            boolean cleaned = callSessionRepository.findById(staleId)
                    .map(stale -> {
                        if (stale.getStatus() == CallStatus.RINGING
                                || stale.getStatus() == CallStatus.ACCEPTED) {
                            long ageSeconds = Duration.between(stale.getCreatedAt(), Instant.now()).getSeconds();
                            if (ageSeconds > 90) {
                                finalizeSession(stale, CallStatus.ENDED, "STALE_CLEANUP");
                                return true;
                            }
                        }
                        return false;
                    })
                    .orElse(true); // call not found → clear map entry
            if (!cleaned) {
                throw new AppException(ErrorCode.BUSY, "Bạn đang trong cuộc gọi khác");
            }
            activeCallByUser.remove(callerId);
        }
        if (activeCallByUser.containsKey(request.getCalleeId())) {
            signalingService.sendToUser(request.getCalleeId(), SignalingService.EVENT_CALL_BUSY,
                    Map.of("calleeId", request.getCalleeId()));
            throw new AppException(ErrorCode.BUSY, "Người nhận đang bận");
        }

        String callId = UUID.randomUUID().toString();
        CallSession session = CallSession.builder()
                .id(callId)
                .conversationId(request.getConversationId())
                .callerId(callerId)
                .calleeId(request.getCalleeId())
                .callerName(request.getCallerName())
                .callerAvatar(request.getCallerAvatar())
                .type(CallType.VOICE)
                .status(CallStatus.RINGING)
                .createdAt(Instant.now())
                .build();
        callSessionRepository.save(session);

        activeCallByUser.put(callerId, callId);
        activeCallByUser.put(request.getCalleeId(), callId);

        Map<String, Object> incomingPayload = new HashMap<>();
        incomingPayload.put("callId", callId);
        incomingPayload.put("conversationId", request.getConversationId());
        incomingPayload.put("callerId", callerId);
        incomingPayload.put("calleeId", request.getCalleeId());
        incomingPayload.put("callerName", request.getCallerName());
        incomingPayload.put("callerAvatar", request.getCallerAvatar());
        incomingPayload.put("type", CallType.VOICE.name());

        signalingService.sendToUser(request.getCalleeId(),
                SignalingService.EVENT_INCOMING_CALL, incomingPayload);

        callPushNotificationService.notifyIncomingCallIfOffline(session);

        return toResponse(session);
    }

    public CallSessionResponse accept(String callId, String userId) {
        CallSession session = getSessionOrThrow(callId);
        if (!userId.equals(session.getCalleeId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() != CallStatus.RINGING) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Cuộc gọi không còn đổ chuông");
        }

        session.setStatus(CallStatus.ACCEPTED);
        session.setAnsweredAt(Instant.now());
        callSessionRepository.save(session);

        Map<String, Object> payload = Map.of("callId", callId);
        signalingService.sendToUser(session.getCallerId(), SignalingService.EVENT_CALL_ACCEPTED, payload);
        return toResponse(session);
    }

    public CallSessionResponse reject(String callId, String userId, String reason) {
        CallSession session = getSessionOrThrow(callId);
        if (!userId.equals(session.getCalleeId()) && !userId.equals(session.getCallerId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() == CallStatus.ENDED
                || session.getStatus() == CallStatus.REJECTED
                || session.getStatus() == CallStatus.MISSED) {
            return toResponse(session);
        }

        CallStatus newStatus = userId.equals(session.getCalleeId())
                ? CallStatus.REJECTED : CallStatus.ENDED;
        finalizeSession(session, newStatus, reason != null ? reason : newStatus.name());

        Map<String, Object> payload = Map.of("callId", callId, "reason", newStatus.name());
        String otherUser = userId.equals(session.getCallerId())
                ? session.getCalleeId() : session.getCallerId();
        signalingService.sendToUser(otherUser, SignalingService.EVENT_CALL_REJECTED, payload);
        return toResponse(session);
    }

    public CallSessionResponse end(String callId, String userId, String reason) {
        CallSession session = getSessionOrThrow(callId);
        if (!userId.equals(session.getCalleeId()) && !userId.equals(session.getCallerId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() == CallStatus.ENDED
                || session.getStatus() == CallStatus.REJECTED
                || session.getStatus() == CallStatus.MISSED) {
            return toResponse(session);
        }

        finalizeSession(session, CallStatus.ENDED, reason != null ? reason : "ENDED");

        Map<String, Object> payload = Map.of("callId", callId);
        String otherUser = userId.equals(session.getCallerId())
                ? session.getCalleeId() : session.getCallerId();
        signalingService.sendToUser(otherUser, SignalingService.EVENT_CALL_ENDED, payload);
        signalingService.sendToUser(userId, SignalingService.EVENT_CALL_ENDED, payload);
        return toResponse(session);
    }

    public void markConnected(String callId, String userId) {
        CallSession session = getSessionOrThrow(callId);
        if (!userId.equals(session.getCalleeId()) && !userId.equals(session.getCallerId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() == CallStatus.CONNECTED) {
            return;
        }
        session.setStatus(CallStatus.CONNECTED);
        callSessionRepository.save(session);
    }

    public CallSession getSessionOrThrow(String callId) {
        return callSessionRepository.findById(callId)
                .orElseThrow(() -> new AppException(ErrorCode.CALL_NOT_FOUND));
    }

    public void relaySignaling(String senderId, String callId, String event, Map<String, Object> payload) {
        CallSession session = getSessionOrThrow(callId);
        if (!senderId.equals(session.getCallerId()) && !senderId.equals(session.getCalleeId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        String targetUser = senderId.equals(session.getCallerId())
                ? session.getCalleeId() : session.getCallerId();
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.putIfAbsent("callId", callId);
        signalingService.sendToUser(targetUser, event, payload);
    }

    public IceServerResponse getIceServers() {
        List<IceServerResponse.IceServerConfig> configs = new ArrayList<>();
        for (Map<String, Object> entry : webRtcProperties.getIceServers()) {
            Object urls = entry.get("urls");
            List<String> urlList;
            if (urls instanceof String s) {
                if (s.isBlank()) {
                    continue;
                }
                urlList = List.of(s.trim());
            } else if (urls instanceof List<?> list) {
                urlList = list.stream()
                        .map(String::valueOf)
                        .filter(u -> u != null && !u.isBlank())
                        .toList();
                if (urlList.isEmpty()) {
                    continue;
                }
            } else {
                continue;
            }
            IceServerResponse.IceServerConfig.IceServerConfigBuilder builder =
                    IceServerResponse.IceServerConfig.builder().urls(urlList);
            if (entry.get("username") != null) {
                builder.username(String.valueOf(entry.get("username")));
            }
            if (entry.get("credential") != null) {
                builder.credential(String.valueOf(entry.get("credential")));
            }
            configs.add(builder.build());
        }
        return IceServerResponse.builder().iceServers(configs).build();
    }

    private void finalizeSession(CallSession session, CallStatus status, String reason) {
        session.setStatus(status);
        session.setEndedAt(Instant.now());
        session.setEndReason(reason);
        if (session.getAnsweredAt() != null && session.getEndedAt() != null) {
            session.setDurationSeconds(Duration.between(session.getAnsweredAt(), session.getEndedAt()).getSeconds());
        }
        callSessionRepository.save(session);
        clearActiveCall(session);
    }

    private void clearActiveCall(CallSession session) {
        activeCallByUser.remove(session.getCallerId(), session.getId());
        activeCallByUser.remove(session.getCalleeId(), session.getId());
    }

    /** Called by CallTimeoutService to evict stale entries */
    public void evictActiveCall(String userId, String callId) {
        activeCallByUser.remove(userId, callId);
    }

    private CallSessionResponse toResponse(CallSession session) {
        return CallSessionResponse.builder()
                .callId(session.getId())
                .conversationId(session.getConversationId())
                .callerId(session.getCallerId())
                .calleeId(session.getCalleeId())
                .callerName(session.getCallerName())
                .callerAvatar(session.getCallerAvatar())
                .type(session.getType())
                .status(session.getStatus())
                .build();
    }
}
