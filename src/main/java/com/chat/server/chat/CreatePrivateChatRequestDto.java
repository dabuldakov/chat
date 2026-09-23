package com.chat.server.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePrivateChatRequestDto {

    @NotNull(message = "Other user UUID is required")
    private UUID otherUserUuid;
}