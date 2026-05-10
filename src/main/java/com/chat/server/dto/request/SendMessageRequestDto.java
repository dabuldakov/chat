package com.chat.server.dto.request;

import com.chat.server.entity.Message;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SendMessageRequestDto {

    @NotBlank(message = "Message text is required")
    private String text;

    private Message.MessageType messageType = Message.MessageType.TEXT;

    private UUID replyToMessageUuid;

    private List<UUID> attachments;
}