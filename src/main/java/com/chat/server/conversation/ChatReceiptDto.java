package com.chat.server.conversation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Квитанция участника чата: докуда его устройство доставило и докуда он прочитал.
 *
 * <p>Один запрос на чат отдаёт все watermark-и, и клиент сам вычисляет статус
 * каждого сообщения — без запроса на каждую строку.
 */
@Data
@Builder
public class ChatReceiptDto {

    private UUID userUuid;
    private String username;
    private String fullName;
    private String avatarUrl;
    private LocalDateTime joinedAt;

    private Long lastDeliveredMessageId;
    private LocalDateTime lastDeliveredAt;
    private Long lastReadMessageId;
    private LocalDateTime lastReadAt;
}
