package com.chat.server.identity;

import com.chat.server.identity.PresenceInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserDto {

    private UUID userUuid;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;

    /** JSON-ключ зафиксирован явно: Lombok даёт геттер isOnline(), а Jackson
     *  срезает префикс is и отдаёт "online" — клиент, ждавший "isOnline",
     *  молча получал false. */
    @JsonProperty("online")
    private boolean isOnline;

    public static UserDto fromEntity(User user, PresenceInfo presence) {
        return UserDto.builder()
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .isOnline(presence.online())
                .build();
    }
}
