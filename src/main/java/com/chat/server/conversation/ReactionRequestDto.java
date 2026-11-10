package com.chat.server.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReactionRequestDto {
    @NotBlank
    @Size(max = 16)
    private String emoji;
}
