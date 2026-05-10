package com.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MessagePreviewDto {

    private UUID messageUuid;
    private String text;
    private Long senderId;
    private UUID senderUuid;
    private String senderName;
    private LocalDateTime createdAt;
}