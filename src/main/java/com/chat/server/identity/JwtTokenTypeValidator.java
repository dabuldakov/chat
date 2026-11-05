package com.chat.server.identity;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Принимает только access-токены IdP (claim {@code type=access}).
 *
 * <p>Защищает от подмены типа токена: если auth-service когда-нибудь начнёт
 * выпускать id/refresh-JWT с тем же issuer/audience, они не должны приниматься
 * ресурс-сервисами как access.
 */
public class JwtTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ACCESS_TYPE = "access";
    private static final String TYPE_CLAIM = "type";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (ACCESS_TYPE.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The required token type is missing", null));
    }
}
