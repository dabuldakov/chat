package com.chat.server.identity;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Фильтр аутентификации по access-токену IdP: публичные пути, валидный/битый
 * токен, выключенный OIDC и отсутствие декодера.
 */
class JwtAuthenticationFilterTest {

    private UserDetailsService userDetailsService;
    private OidcUserProvisioner provisioner;
    private ObjectProvider<JwtDecoder> decoderProvider;
    private JwtDecoder decoder;
    private ServiceTokenService serviceTokens;
    private UserService users;
    private FilterChain chain;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        userDetailsService = mock(UserDetailsService.class);
        provisioner = mock(OidcUserProvisioner.class);
        decoderProvider = mock(ObjectProvider.class);
        decoder = mock(JwtDecoder.class);
        serviceTokens = mock(ServiceTokenService.class);
        users = mock(UserService.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthenticationFilter(
                userDetailsService, provisioner,
                new OidcProperties(true, "issuer", "jwks", List.of()),
                decoderProvider, serviceTokens, users
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String path, String token) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    void publicEndpointSkipsAuthentication() throws Exception {
        filter.doFilter(request("GET", "/api/avatars/x", null),
                new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(decoderProvider);
    }

    @Test
    void validTokenAuthenticatesUser() throws Exception {
        UUID subject = UUID.randomUUID();
        User user = User.builder()
                .userId(7L)
                .userUuid(subject)
                .username("alice")
                .build();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("username", "alice")
                .build();
        UserDetails details = mock(UserDetails.class);

        when(decoderProvider.getIfAvailable()).thenReturn(decoder);
        when(decoder.decode("token")).thenReturn(jwt);
        when(provisioner.findOrCreate(subject, "alice")).thenReturn(user);
        when(userDetailsService.loadUserByUsername(subject.toString())).thenReturn(details);

        filter.doFilter(request("GET", "/api/chats", "token"),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(details);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void invalidTokenLeavesAnonymous() throws Exception {
        when(decoderProvider.getIfAvailable()).thenReturn(decoder);
        when(decoder.decode("bad")).thenThrow(new JwtException("bad"));

        filter.doFilter(request("GET", "/api/chats", "bad"),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void disabledOidcDoesNotAuthenticate() throws Exception {
        filter = new JwtAuthenticationFilter(
                userDetailsService, provisioner,
                new OidcProperties(false, "issuer", "jwks", List.of()),
                decoderProvider, serviceTokens, users
        );

        filter.doFilter(request("GET", "/api/chats", "token"),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(decoderProvider);
    }

    @Test
    void missingDecoderDoesNotAuthenticate() throws Exception {
        when(decoderProvider.getIfAvailable()).thenReturn(null);

        filter.doFilter(request("GET", "/api/chats", "token"),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void requestWithoutBearerHeaderIsSkipped() throws Exception {
        filter.doFilter(request("GET", "/api/chats", null),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void optionsRequestIsNotFiltered() {
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/chats", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/chats", null))).isFalse();
    }
}
