package com.chat.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BlockUserRequestDto {

    @NotNull(message = "User UUID to block is required")
    private UUID blockUserUuid;

    private String reason;
}