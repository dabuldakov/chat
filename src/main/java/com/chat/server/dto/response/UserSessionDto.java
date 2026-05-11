package com.chat.server.dto.response;

import com.chat.server.entity.UserSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserSessionDto {

    private UUID sessionUuid;
    private Long sessionId;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime lastActivity;
    private LocalDateTime expiresAt;
    private boolean isCurrent;
    private boolean isActive;

    public static UserSessionDto fromEntity(UserSession session, boolean isCurrent) {
        return UserSessionDto.builder()
                .sessionUuid(session.getSessionUuid())
                .sessionId(session.getSessionId())
                .deviceId(session.getDeviceId())
                .deviceName(session.getDeviceName())
                .deviceType(session.getDeviceType())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .lastActivity(session.getLastActivity())
                .expiresAt(session.getExpiresAt())
                .isCurrent(isCurrent)
                .isActive(session.getIsActive())
                .build();
    }
}