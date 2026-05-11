package com.chat.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddContactRequestDto {

    @NotNull(message = "Contact user UUID is required")
    private UUID contactUserUuid;

    private String contactName;
}