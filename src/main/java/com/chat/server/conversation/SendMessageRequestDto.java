package com.chat.server.conversation;

import com.chat.server.conversation.gif.GifPayload;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SendMessageRequestDto {

    // Для текстовых сообщений обязателен, но проверяется в контроллере: у
    // GIF-сообщения текста нет (контент — вложение).
    private String text;

    private Message.MessageType messageType = Message.MessageType.TEXT;

    private UUID replyToMessageUuid;

    /** Клиентский UUID для идемпотентной повторной отправки. */
    private UUID clientMessageId;

    private List<UUID> attachments;

    /** 0..100 — сила удержания кнопки отправки. */
    private Integer power;

    /** Метаданные гифки при messageType=GIF (поиск делает клиент у провайдера). */
    private GifPayload gif;
}