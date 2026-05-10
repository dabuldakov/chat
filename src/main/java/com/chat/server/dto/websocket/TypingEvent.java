package com.chat.server.dto.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TypingEvent {

    private UUID chatUuid;
    private Long userId;
    private boolean isTyping;
}