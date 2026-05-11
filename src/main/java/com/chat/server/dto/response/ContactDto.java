package com.chat.server.dto.response;

import com.chat.server.entity.Contact;
import com.chat.server.entity.User;
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
    private boolean isOnline;
    private LocalDateTime lastSeenAt;
    private LocalDateTime addedAt;

    public static ContactDto fromEntity(Contact contact, User contactUser) {
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
                .isOnline(contactUser != null && Boolean.TRUE.equals(contactUser.getIsOnline()))
                .lastSeenAt(contactUser != null ? contactUser.getLastSeenAt() : null)
                .addedAt(contact.getCreatedAt())
                .build();
    }
}