package com.chat.server.dto.response;

import com.chat.server.entity.User;
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
    private boolean isOnline;
    private LocalDateTime lastSeenAt;
    private boolean emailVerified;
    private LocalDateTime createdAt;

    public static UserProfileDto fromEntity(User user) {
        return UserProfileDto.builder()
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .phoneNumber(user.getPhoneNumber())
                .isOnline(user.getIsOnline() != null && user.getIsOnline())
                .lastSeenAt(user.getLastSeenAt())
                .emailVerified(user.getEmailVerified() != null && user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}