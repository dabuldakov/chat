package com.chat.server.auth;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponseDto {

    private String token;
    private String refreshToken;
    private UUID userUuid;
    private String username;
    private String email;
    private String avatarUrl;
}