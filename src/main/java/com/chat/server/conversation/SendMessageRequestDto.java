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

    private List<UUID> attachments;

    /** Метаданные гифки при messageType=GIF (поиск делает клиент у провайдера). */
    private GifPayload gif;
}