package com.chat.server.service;

import com.chat.server.dto.websocket.MessageEvent;
import com.chat.server.dto.websocket.TypingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void sendMessageToChat(UUID chatUuid, MessageEvent event) {
        String destination = "/topic/chats/" + chatUuid + "/messages";
        messagingTemplate.convertAndSend(destination, event);
        log.debug("WebSocket message sent to: {}", destination);
    }

    public void sendTypingIndicator(UUID chatUuid, TypingEvent event) {
        String destination = "/topic/chats/" + chatUuid + "/typing";
        messagingTemplate.convertAndSend(destination, event);
    }

    public void sendReadReceipt(UUID chatUuid, UUID userId, UUID lastReadMessageUuid) {
        String destination = "/topic/chats/" + chatUuid + "/read";
        messagingTemplate.convertAndSend(destination, Map.of(
                "userId", userId,
                "lastReadMessageUuid", lastReadMessageUuid
        ));
    }
}