package com.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageStatusDto {

    private Long userId;
    private String status; // SENT, DELIVERED, READ, FAILED
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
}