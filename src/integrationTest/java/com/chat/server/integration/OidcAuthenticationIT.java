package com.chat.server.integration;

import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полный HTTP-путь OIDC-аутентификации: фильтр принимает access-токен IdP,
 * провижинит локального пользователя по sub и пускает в защищённый эндпоинт.
 */
@TestPropertySource(properties = "auth.oidc.enabled=true")
@AutoConfigureMockMvc
class OidcAuthenticationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void validTokenProvisionsUserAndAllowsRequest() throws Exception {
        UUID subject = UUID.randomUUID();
        when(jwtDecoder.decode("good-token")).thenReturn(Jwt.withTokenValue("good-token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("username", "oidc-chat")
                .claim("type", "access")
                .build());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("oidc-chat"));

        assertThat(userRepository.findByAuthSubject(subject)).isPresent();
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("invalid token"));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer broken"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.count()).isZero();
    }
}
