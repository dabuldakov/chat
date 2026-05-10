package com.chat.server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateChatRequestDto {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private String avatarUrl;
}