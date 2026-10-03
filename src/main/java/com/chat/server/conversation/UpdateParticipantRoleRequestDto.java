package com.chat.server.conversation;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateParticipantRoleRequestDto {

    @NotNull(message = "Role is required")
    private Participant.ParticipantRole role;
}