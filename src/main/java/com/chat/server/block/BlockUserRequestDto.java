package com.chat.server.block;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

import com.chat.server.user.User;
@Data
public class BlockUserRequestDto {

    @NotNull(message = "User UUID to block is required")
    private UUID blockUserUuid;

    private String reason;
}