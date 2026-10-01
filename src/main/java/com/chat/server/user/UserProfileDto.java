package com.chat.server.user;

import com.chat.server.presence.PresenceInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserProfileDto {

    private UUID userUuid;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private String phoneNumber;

    /** JSON-ключ зафиксирован явно: Lombok даёт геттер isOnline(), а Jackson
     *  срезает префикс is и отдаёт "online" — клиент, ждавший "isOnline",
     *  молча получал false. */
    @JsonProperty("online")
    private boolean isOnline;

    private LocalDateTime lastSeenAt;
    private boolean emailVerified;
    private LocalDateTime createdAt;

    public static UserProfileDto fromEntity(User user, PresenceInfo presence) {
        return UserProfileDto.builder()
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .phoneNumber(user.getPhoneNumber())
                .isOnline(presence.online())
                .lastSeenAt(presence.lastSeenAt())
                .emailVerified(user.getEmailVerified() != null && user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
