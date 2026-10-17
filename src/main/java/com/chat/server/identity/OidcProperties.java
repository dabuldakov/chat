package com.chat.server.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Настройки приёма access-токенов единого IdP (auth-service).
 * Токены подписаны RS256; публичный ключ берётся из JWKS.
 */
@ConfigurationProperties(prefix = "auth.oidc")
public record OidcProperties(
        boolean enabled,
        String issuer,
        String jwksUri,
        List<String> audience,
        boolean linkByUsername
) {
}
