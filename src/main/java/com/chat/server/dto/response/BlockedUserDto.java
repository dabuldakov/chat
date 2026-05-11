package com.chat.server.dto.response;

import com.chat.server.entity.BlockedUser;
import com.chat.server.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BlockedUserDto {

    private UUID blockUuid;
    private Long blockedUserId;
    private UUID blockedUserUuid;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private String reason;
    private LocalDateTime blockedAt;

    public static BlockedUserDto fromEntity(BlockedUser blockedUser, User blockedUserInfo) {
        return BlockedUserDto.builder()
                .blockUuid(blockedUser.getBlockUuid())
                .blockedUserId(blockedUser.getBlockedUserId())
                .blockedUserUuid(blockedUserInfo != null ? blockedUserInfo.getUserUuid() : null)
                .username(blockedUserInfo != null ? blockedUserInfo.getUsername() : null)
                .firstName(blockedUserInfo != null ? blockedUserInfo.getFirstName() : null)
                .lastName(blockedUserInfo != null ? blockedUserInfo.getLastName() : null)
                .fullName(blockedUserInfo != null ? blockedUserInfo.getFullName() : null)
                .avatarUrl(blockedUserInfo != null ? blockedUserInfo.getAvatarUrl() : null)
                .reason(blockedUser.getReason())
                .blockedAt(blockedUser.getCreatedAt())
                .build();
    }
}