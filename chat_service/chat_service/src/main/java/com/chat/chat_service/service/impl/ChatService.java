package com.chat.chat_service.service.impl;

import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.request.NotificationMobileRequest;
import com.chat.chat_service.dto.response.ChatMessageResponse;
import com.chat.chat_service.entity.ChatMessage;
import com.chat.chat_service.entity.Conversation;
import com.chat.chat_service.entity.ParticipantInfo;
import com.chat.chat_service.exception.AppException;
import com.chat.chat_service.exception.ErrorCode;
import com.chat.chat_service.mapper.ChatMapper;
import com.chat.chat_service.repository.ChatMessageRepository;
import com.chat.chat_service.repository.ConservationRepository;
import com.chat.chat_service.repository.httpclient.NotificationClient;
import com.chat.chat_service.repository.httpclient.ProfileClient;
import com.chat.chat_service.service.IChatService;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CHAT_SERVICE")
public class ChatService implements IChatService {

    private final ConservationRepository conservationRepository;
    private final ProfileClient profileClient;
    private final NotificationClient notificationClient;
    private final ChatMapper chatMapper;
    private final ChatMessageRepository chatMessageRepository;

    @Autowired(required = false)
    private SocketIOServer socketIOServer;

    @Override
    public ChatMessageResponse createChatMessage(ChatMessageRequest request) {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();

        Conversation conversation = conservationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));
        if (!isParticipant) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        Conversation refreshedConversation = conservationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        var profileResponse = profileClient.getUserProfileById(currentUserId);
        if (profileResponse == null || profileResponse.getResult() == null) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        var profile = profileResponse.getResult();

        ParticipantInfo sender = ParticipantInfo.builder()
                .userId(profile.getUserId())
                .userName(profile.getUserName())
                .avatar(profile.getAvatar())
                .build();

        boolean isE2e = hasClientIv(request.getIv());

        ChatMessage chatMessage = ChatMessage.builder()
                .conversationId(request.getConversationId())
                .message(request.getMessage())
                .iv(request.getIv())
                .encrypted(isE2e)
                .e2e(isE2e)
                .sender(sender)
                .createdDate(Instant.now())
                .storyReplyMediaUrl(blankToNull(request.getStoryReplyMediaUrl()))
                .storyReplyOwnerId(blankToNull(request.getStoryReplyOwnerId()))
                .build();

        chatMessage = chatMessageRepository.save(chatMessage);

        ChatMessageResponse response = chatMapper.convertFromChatMessage(chatMessage);
        response.setId(chatMessage.getId());
        response.setMe(true);

        final ChatMessage savedMessage = chatMessage;
        refreshedConversation.getParticipants().stream()
                .filter(p -> !p.getUserId().equals(currentUserId))
                .findFirst()
                .ifPresent(receiver -> {
                    try {
                        notificationClient.sendMobileNotification(
                                NotificationMobileRequest.builder()
                                        .userId(receiver.getUserId())
                                        .tittle(profile.getUserName())
                                        .body(resolveNotificationBody(request))
                                        .build()
                        );
                    } catch (Exception e) {
                        log.warn("Failed to send push notification: {}", e.getMessage());
                    }

                    if (socketIOServer != null) {
                        try {
                            Map<String, Object> senderMap = new HashMap<>();
                            senderMap.put("userId", sender.getUserId());
                            senderMap.put("userName", sender.getUserName());
                            senderMap.put("avatar", sender.getAvatar());

                            Map<String, Object> broadcastPayload = new HashMap<>();
                            broadcastPayload.put("id", savedMessage.getId());
                            broadcastPayload.put("conversationId", savedMessage.getConversationId());
                            broadcastPayload.put("message", savedMessage.getMessage());
                            broadcastPayload.put("iv", savedMessage.getIv());
                            broadcastPayload.put("encrypted", savedMessage.isEncrypted());
                            broadcastPayload.put("e2e", savedMessage.isE2e());
                            broadcastPayload.put("sender", senderMap);
                            broadcastPayload.put("createdDate", savedMessage.getCreatedDate().toString());
                            broadcastPayload.put("me", false);
                            if (savedMessage.getStoryReplyMediaUrl() != null) {
                                broadcastPayload.put("storyReplyMediaUrl", savedMessage.getStoryReplyMediaUrl());
                            }
                            if (savedMessage.getStoryReplyOwnerId() != null) {
                                broadcastPayload.put("storyReplyOwnerId", savedMessage.getStoryReplyOwnerId());
                            }

                            socketIOServer.getRoomOperations(request.getConversationId())
                                    .sendEvent("send_message", broadcastPayload);

                            log.info("Broadcasted message to Socket.IO room: {}", request.getConversationId());
                        } catch (Exception e) {
                            log.warn("Failed to broadcast Socket.IO message: {}", e.getMessage());
                        }
                    }
                });

        return response;
    }

    @Override
    public List<ChatMessageResponse> getAllMessages(String conversationId) {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();

        Conversation conversation = conservationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));
        if (!isParticipant) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        List<ChatMessage> messages =
                chatMessageRepository.findAllByConversationIdOrderByCreatedDateDesc(conversationId);

        return messages.stream()
                .map(msg -> {
                    ChatMessageResponse response = chatMapper.convertFromChatMessage(msg);
                    response.setId(msg.getId());
                    response.setMe(msg.getSender() != null && currentUserId.equals(msg.getSender().getUserId()));
                    return response;
                })
                .toList();
    }

    @Override
    public ChatMessageResponse recallMessage(String messageId) {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();

        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (chatMessage.isRecalled()) {
            throw new AppException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }

        if (chatMessage.getSender() == null
                || !currentUserId.equals(chatMessage.getSender().getUserId())) {
            throw new AppException(ErrorCode.MESSAGE_RECALL_FORBIDDEN);
        }

        Conversation conversation = conservationRepository.findById(chatMessage.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));
        if (!isParticipant) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        var profileResponse = profileClient.getUserProfileById(currentUserId);
        if (profileResponse == null || profileResponse.getResult() == null) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        var profile = profileResponse.getResult();
        String lastName = profile.getLastName();
        if (lastName == null || lastName.isBlank()) {
            lastName = profile.getUserName() != null ? profile.getUserName() : "Người dùng";
        }

        chatMessage.setRecalled(true);
        chatMessage.setRecalledByLastName(lastName.trim());
        chatMessage.setRecalledAt(Instant.now());
        chatMessage.setMessage(null);
        chatMessage.setIv(null);
        chatMessage.setEncrypted(false);
        chatMessage.setE2e(false);
        chatMessage = chatMessageRepository.save(chatMessage);

        ChatMessageResponse response = chatMapper.convertFromChatMessage(chatMessage);
        response.setId(chatMessage.getId());
        response.setMe(true);

        broadcastRecall(chatMessage, lastName.trim());

        return response;
    }

    private void broadcastRecall(ChatMessage chatMessage, String recalledByLastName) {
        if (socketIOServer == null) {
            return;
        }
        try {
            Map<String, Object> broadcastPayload = new HashMap<>();
            broadcastPayload.put("id", chatMessage.getId());
            broadcastPayload.put("conversationId", chatMessage.getConversationId());
            broadcastPayload.put("recalled", true);
            broadcastPayload.put("recalledByLastName", recalledByLastName);
            broadcastPayload.put("createdDate", chatMessage.getCreatedDate() != null
                    ? chatMessage.getCreatedDate().toString() : null);
            broadcastPayload.put("me", false);

            socketIOServer.getRoomOperations(chatMessage.getConversationId())
                    .sendEvent("recall_message", broadcastPayload);

            log.info("Broadcasted recall to Socket.IO room: {}", chatMessage.getConversationId());
        } catch (Exception e) {
            log.warn("Failed to broadcast recall event: {}", e.getMessage());
        }
    }

    private static boolean hasClientIv(String iv) {
        return iv != null && !iv.isBlank();
    }

    private static String resolveNotificationBody(ChatMessageRequest request) {
        if (request.getNotificationPreview() != null && !request.getNotificationPreview().isBlank()) {
            return request.getNotificationPreview();
        }
        return request.getMessage();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
