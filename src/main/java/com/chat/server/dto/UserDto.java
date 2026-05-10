package com.chat.server.dto.response;

import com.chat.server.entity.User;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserDto {

    private UUID userUuid;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private boolean isOnline;

    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .isOnline(user.getIsOnline() != null && user.getIsOnline())
                .build();
    }
}