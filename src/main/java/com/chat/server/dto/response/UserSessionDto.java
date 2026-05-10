package com.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserSessionDto {

    private Long sessionId;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    private LocalDateTime lastActivity;
    private boolean isCurrent;
}