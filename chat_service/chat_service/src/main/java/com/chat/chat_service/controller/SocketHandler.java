package com.chat.chat_service.controller;

import com.chat.chat_service.configuration.SocketAuthContext;
import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.request.IntrospectRequest;
import com.chat.chat_service.entity.WebSocketSession;
import com.chat.chat_service.service.impl.ChatService;
import com.chat.chat_service.service.impl.IdentityService;
import com.chat.chat_service.service.impl.WebSocketSessionService;
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
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Component
public class SocketHandler {
    SocketIOServer server;
    IdentityService identityService;
    WebSocketSessionService webSocketSessionService;
    ChatService chatService;

    @OnConnect
    public void clientConnected(SocketIOClient client){
        //Get token from request params
        String token = client.getHandshakeData().getSingleUrlParam("token");

        //Verify token
        var introspect = identityService.introspect(IntrospectRequest.builder().token(token).build());

        //Check requirement
        if(introspect.isValid()){
            log.info("Client connnected: {}" , client.getSessionId());
            client.set("token", token);

            //Persist webSocketSession
            WebSocketSession webSocketSession = WebSocketSession.builder()
                    .socketSessionId(client.getSessionId().toString())
                    .userId(introspect.getUserId())
                    .createdAt(Instant.now())
                    .build();

            webSocketSession = webSocketSessionService.create(webSocketSession);

            log.info("Web socket session created with id: {}" , webSocketSession.getId());
        }else{
            log.info("Authentication failed: {}" , client.getSessionId());
            client.disconnect();
        }
    }

    @OnDisconnect
    public void clientDisconnected(SocketIOClient client) {
        log.info("Client disConnected: {}", client.getSessionId());
        webSocketSessionService.deleteSession(client.getSessionId().toString());
    }

    @PostConstruct
    public void startServer() {
        server.addListeners(this);
        server.start();
        log.info("Socket server started");
    }

    @PreDestroy
    public void stopServer() {
        server.stop();
        log.info("Socket server stopped");
    }


    @OnEvent("join")
    public void onJoin(SocketIOClient client, Object data) {
        String conversationId = extractConversationId(data);
        if (conversationId != null && !conversationId.isEmpty()) {
            client.joinRoom(conversationId);
            log.info("Client {} joined room {}", client.getSessionId(), conversationId);
        } else {
            log.warn("Client {} join ignored — could not parse conversationId from {}", client.getSessionId(), data);
        }
    }

    @OnEvent("send_message")
    public void onMessage(
            SocketIOClient client,
            ChatMessageRequest request
    ) {
        try {

            String token = client.get("token");

            SocketAuthContext.setToken(token);

            chatService.createChatMessage(request);

        } finally {
            SocketAuthContext.clear();
        }
    }

    private static String extractConversationId(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String s) {
            return s.trim().isEmpty() ? null : s.trim();
        }
        if (data instanceof Map<?, ?> map) {
            Object value = map.get("conversationId");
            return value != null ? value.toString() : null;
        }
        // JSONObject / Jackson types from socket.io client
        try {
            var method = data.getClass().getMethod("getString", String.class);
            Object value = method.invoke(data, "conversationId");
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }
        return data.toString();
    }

}
