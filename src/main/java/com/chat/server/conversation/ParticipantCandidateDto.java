package com.chat.server.conversation;

import com.chat.server.identity.User;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Пользователь, которого можно добавить в групповой чат. */
@Data
@Builder
public class ParticipantCandidateDto {

    private Long userId;
    private UUID userUuid;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    /** true — пользователь есть в контактах запросившего. */
    private boolean isContact;

    public static ParticipantCandidateDto fromUser(User user, boolean isContact) {
        return ParticipantCandidateDto.builder()
                .userId(user.getUserId())
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .isContact(isContact)
                .build();
    }
}
