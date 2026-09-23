package com.chat.server.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateParticipantRoleRequestDto {

    @NotNull(message = "Role is required")
    private Participant.ParticipantRole role;
}