package com.chat.server.dto.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MessageEvent {

    private String type; // NEW_MESSAGE, EDIT_MESSAGE, DELETE_MESSAGE
    private UUID messageUuid;
    private UUID chatUuid;
    private Long senderId;
    private String text;
    private String messageType;
    private Long timestamp;
}