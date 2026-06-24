package com.call.call_service.controller;

import com.call.call_service.configuration.CallJwtHelper;
import com.call.call_service.dto.request.IntrospectRequest;
import com.call.call_service.entity.CallWebSocketSession;
import com.call.call_service.service.impl.CallSessionService;
import com.call.call_service.service.impl.CallWebSocketSessionService;
import com.call.call_service.service.impl.IdentityService;
import com.call.call_service.service.impl.SignalingService;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallSocketHandler {

    SocketIOServer server;
    IdentityService identityService;
    CallWebSocketSessionService webSocketSessionService;
    CallSessionService callSessionService;
    CallJwtHelper callJwtHelper;

    AtomicBoolean socketStarted = new AtomicBoolean(false);

    @OnConnect
    public void onConnect(SocketIOClient client) {
        String token = client.getHandshakeData().getSingleUrlParam("token");
        if (token == null || token.isBlank()) {
            log.info("Call socket auth failed (missing token): {}", client.getSessionId());
            client.disconnect();
            return;
        }

        var introspect = identityService.introspect(IntrospectRequest.builder().token(token).build());
        String userId = introspect.getUserId();
        if (userId == null || userId.isBlank()) {
            userId = callJwtHelper.extractUserId(token);
        }

        if (!introspect.isValid() || userId == null || userId.isBlank()) {
            log.info("Call socket auth failed: {} (valid={}, userId={})",
                    client.getSessionId(), introspect.isValid(), userId);
            client.disconnect();
            return;
        }
        client.set("userId", userId);
        client.joinRoom(SignalingService.userRoom(userId));

        CallWebSocketSession session = CallWebSocketSession.builder()
                .socketSessionId(client.getSessionId().toString())
                .userId(userId)
                .createdAt(Instant.now())
                .build();
        webSocketSessionService.create(session);
        log.info("Call socket connected userId={} session={}", userId, client.getSessionId());
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        webSocketSessionService.deleteSession(client.getSessionId().toString());
        log.info("Call socket disconnected session={}", client.getSessionId());
    }

    @OnEvent("webrtc_offer")
    public void onOffer(SocketIOClient client, Map<String, Object> data) {
        relay(client, SignalingService.EVENT_WEBRTC_OFFER, data);
    }

    @OnEvent("webrtc_answer")
    public void onAnswer(SocketIOClient client, Map<String, Object> data) {
        relay(client, SignalingService.EVENT_WEBRTC_ANSWER, data);
    }

    @OnEvent("webrtc_ice_candidate")
    public void onIceCandidate(SocketIOClient client, Map<String, Object> data) {
        relay(client, SignalingService.EVENT_WEBRTC_ICE, data);
    }

    private void relay(SocketIOClient client, String event, Map<String, Object> data) {
        String userId = client.get("userId");
        if (userId == null || data == null) {
            return;
        }
        Object callIdObj = data.get("callId");
        if (callIdObj == null) {
            return;
        }
        try {
            callSessionService.relaySignaling(userId, callIdObj.toString(), event, new HashMap<>(data));
        } catch (Exception e) {
            log.warn("Relay {} failed: {}", event, e.getMessage());
        }
    }

    @PostConstruct
    public void start() {
        if (socketStarted.get()) {
            return;
        }
        server.addListeners(this);
        try {
            server.start();
            socketStarted.set(true);
            log.info("Call signaling socket started on port {}", server.getConfiguration().getPort());
        } catch (Exception e) {
            log.error("Call signaling socket failed to start (port {} may be in use). "
                    + "Stop the old call_service instance first: {}",
                    server.getConfiguration().getPort(), e.getMessage());
            throw e;
        }
    }

    @PreDestroy
    public void stop() {
        if (!socketStarted.get()) {
            return;
        }
        try {
            server.stop();
            log.info("Call signaling socket stopped");
        } finally {
            socketStarted.set(false);
        }
    }
}
