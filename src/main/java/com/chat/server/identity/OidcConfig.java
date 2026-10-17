package com.chat.server.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Декодер access-токенов auth-service: подпись проверяется по JWKS
 * (RS256), плюс проверки issuer и audience. Бин появляется только при
 * {@code auth.oidc.enabled=true}, иначе сервис работает как раньше (HS256).
 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
public class OidcConfig {

    @Bean
    @ConditionalOnProperty(name = "auth.oidc.enabled", havingValue = "true")
    public JwtDecoder oidcJwtDecoder(OidcProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new AudienceValidator(properties.audience()));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
