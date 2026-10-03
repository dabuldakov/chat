package com.chat.server.conversation;

import com.chat.server.identity.PresenceInfo;
import com.chat.server.identity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** JSON-ключ зафиксирован явно: у Lombok-поля boolean isOnline Jackson срезает
     *  префикс is и отдаёт "online" — клиент, ждавший "isOnline", получал false. */
    @JsonProperty("online")
    private boolean isOnline;

    private LocalDateTime lastSeenAt;
    private String nickname;
    private boolean isMuted;
    private LocalDateTime mutedUntil;
    private boolean isPinned;

    public static ParticipantInfoDto fromEntity(Participant participant, User user, PresenceInfo presence) {
        return ParticipantInfoDto.builder()
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .isOnline(presence.online())
                .lastSeenAt(presence.lastSeenAt())
                .nickname(participant.getNickname())
                .isMuted(participant.isMuted())
                .mutedUntil(participant.getMutedUntil())
                .isPinned(participant.getIsPinned() != null && participant.getIsPinned())
                .build();
    }
}