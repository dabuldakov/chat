package com.chat.server.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AddParticipantsRequestDto {

    @NotNull(message = "Member UUIDs are required")
    private List<UUID> memberUuids;
}