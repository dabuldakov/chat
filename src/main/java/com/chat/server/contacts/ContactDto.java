package com.chat.server.contacts;

import com.chat.server.identity.PresenceInfo;
import com.chat.server.identity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ContactDto {

    private UUID contactUuid;
    private Long contactUserId;
    private UUID contactUserUuid;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private String contactName;

    /**
     * Явно зафиксированное JSON-имя. Без аннотации Lombok генерирует геттер
     * {@code isOnline()}, а Jackson по умолчанию срезает префикс {@code is} и
     * отдаёт ключ {@code "online"} — клиент ждал {@code "isOnline"} и всегда
     * получал {@code false}. Имя контракта должно быть видимым в коде.
     */
    @JsonProperty("online")
    private boolean isOnline;

    @JsonProperty("lastSeenAt")
    private LocalDateTime lastSeenAt;

    private LocalDateTime addedAt;

    public static ContactDto fromEntity(Contact contact, User contactUser, PresenceInfo presence) {
        return ContactDto.builder()
                .contactUuid(contact.getContactUuid())
                .contactUserId(contact.getContactUserId())
                .contactUserUuid(contactUser != null ? contactUser.getUserUuid() : null)
                .username(contactUser != null ? contactUser.getUsername() : null)
                .firstName(contactUser != null ? contactUser.getFirstName() : null)
                .lastName(contactUser != null ? contactUser.getLastName() : null)
                .fullName(contactUser != null ? contactUser.getFullName() : null)
                .avatarUrl(contactUser != null ? contactUser.getAvatarUrl() : null)
                .contactName(contact.getContactName())
                .isOnline(presence.online())
                .lastSeenAt(presence.lastSeenAt())
                .addedAt(contact.getCreatedAt())
                .build();
    }
}
