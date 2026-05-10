package com.chat.server.dto.response;

import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ParticipantInfoDto {

    private UUID userUuid;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private String role;
    private LocalDateTime joinedAt;
    private boolean isOnline;
    private LocalDateTime lastSeenAt;
    private String nickname;
    private boolean isMuted;
    private LocalDateTime mutedUntil;
    private boolean isPinned;

    public static ParticipantInfoDto fromEntity(Participant participant, User user) {
        return ParticipantInfoDto.builder()
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .isOnline(user.getIsOnline() != null && user.getIsOnline())
                .lastSeenAt(user.getLastSeenAt())
                .nickname(participant.getNickname())
                .isMuted(participant.isMuted())
                .mutedUntil(participant.getMutedUntil())
                .isPinned(participant.getIsPinned() != null && participant.getIsPinned())
                .build();
    }
}