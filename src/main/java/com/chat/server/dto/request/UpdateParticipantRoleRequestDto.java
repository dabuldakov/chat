package com.chat.server.dto.request;

import com.chat.server.entity.Participant;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateParticipantRoleRequestDto {

    @NotNull(message = "Role is required")
    private Participant.ParticipantRole role;
}