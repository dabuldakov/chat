package com.chat.server.message;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ForwardMessageRequestDto {

    @NotNull(message = "Message UUID is required")
    private UUID messageUuid;

    @NotNull(message = "Target chat UUID is required")
    private UUID targetChatUuid;
}