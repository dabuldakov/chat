package com.chat.server.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditMessageRequestDto {

    @NotBlank(message = "Message text is required")
    private String text;
}