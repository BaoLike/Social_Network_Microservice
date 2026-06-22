package com.chat.chat_service.mapper;

import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.response.ChatMessageResponse;
import com.chat.chat_service.entity.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMapper {

    public ChatMessageResponse convertFromChatMessage (ChatMessage chatMessage){
        return  ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .conversationId(chatMessage.getConversationId())
                .message(chatMessage.getMessage())
                .iv(chatMessage.getIv())
                .encrypted(chatMessage.isEncrypted())
                .e2e(chatMessage.isE2e())
                .sender(chatMessage.getSender())
                .createdDate(chatMessage.getCreatedDate())
                .recalled(chatMessage.isRecalled())
                .recalledByLastName(chatMessage.getRecalledByLastName())
                .recalledAt(chatMessage.getRecalledAt())
                .storyReplyMediaUrl(chatMessage.getStoryReplyMediaUrl())
                .storyReplyOwnerId(chatMessage.getStoryReplyOwnerId())
                .build();
    }

    public ChatMessage convertChatMessageFromRequest(ChatMessageRequest request){

        return ChatMessage.builder()
                .conversationId(request.getConversationId())
                .message(request.getMessage())
                .build();
    }
}
