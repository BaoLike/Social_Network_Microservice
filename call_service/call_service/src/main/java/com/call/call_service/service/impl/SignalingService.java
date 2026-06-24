package com.call.call_service.service.impl;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SignalingService {

    public static final String EVENT_INCOMING_CALL = "incoming_call";
    public static final String EVENT_CALL_ACCEPTED = "call_accepted";
    public static final String EVENT_CALL_REJECTED = "call_rejected";
    public static final String EVENT_CALL_ENDED = "call_ended";
    public static final String EVENT_CALL_BUSY = "call_busy";
    public static final String EVENT_WEBRTC_OFFER = "webrtc_offer";
    public static final String EVENT_WEBRTC_ANSWER = "webrtc_answer";
    public static final String EVENT_WEBRTC_ICE = "webrtc_ice_candidate";

    SocketIOServer server;

    public void sendToUser(String userId, String event, Object payload) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String room = userRoom(userId);
        server.getRoomOperations(room).sendEvent(event, payload);
        log.debug("Signaling {} -> user {} room {}", event, userId, room);
    }

    public boolean isUserOnline(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        var clients = server.getRoomOperations(userRoom(userId)).getClients();
        return clients != null && !clients.isEmpty();
    }

    public static String userRoom(String userId) {
        return "user:" + userId;
    }
}
