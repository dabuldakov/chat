package com.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditMessageRequestDto {

    @NotBlank(message = "Message text is required")
    private String text;
}