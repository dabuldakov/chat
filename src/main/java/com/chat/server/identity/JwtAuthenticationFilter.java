package com.chat.server.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Аутентификация по access-токену единого IdP (auth-service, RS256):
 * токен проверяется по JWKS, пользователь находится/создаётся по claim
 * {@code sub}. В SecurityContext кладётся principal, у которого
 * {@code getName()} — числовой {@code users.user_id}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final OidcUserProvisioner oidcUserProvisioner;
    private final OidcProperties oidcProperties;
    private final ObjectProvider<JwtDecoder> oidcDecoderProvider;
    private final ServiceTokenService serviceTokenService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getServletPath();

        // Пропускаем публичные эндпоинты
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractToken(request);

            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Сначала пробуем service-токен бота: он не JWT и не ходит в IdP.
                if (!authenticateWithServiceToken(token, request)) {
                    authenticateWithOidc(token, request);
                }
            }
        } catch (Exception e) {
            log.warn("Authentication error for request {}: {}", requestPath, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Токен бота/интеграции (mz_...). Возвращает true, если он принят. */
    private boolean authenticateWithServiceToken(String token, HttpServletRequest request) {
        if (!token.startsWith("mz_")) {
            return false;
        }
        try {
            return serviceTokenService.authenticate(token)
                    .map(userId -> {
                        User user = userService.getUserById(userId);
                        if (Boolean.TRUE.equals(user.getIsDeleted())) {
                            return false;
                        }
                        UserDetails userDetails = userDetailsService
                                .loadUserByUsername(user.getUserUuid().toString());
                        SecurityContextHolder.getContext()
                                .setAuthentication(createAuthentication(userDetails, request));
                        log.debug("Authenticated service token as chat user {}", userId);
                        return true;
                    })
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Service token authentication failed: {}", e.getMessage());
            return false;
        }
    }

    /** Access-токен auth-service (RS256). Возвращает true, если он принят. */
    private boolean authenticateWithOidc(String token, HttpServletRequest request) {
        if (!oidcProperties.enabled()) {
            return false;
        }
        JwtDecoder decoder = oidcDecoderProvider.getIfAvailable();
        if (decoder == null) {
            return false;
        }
        try {
            Jwt jwt = decoder.decode(token);
            UUID subject = UUID.fromString(jwt.getSubject());
            User user = oidcUserProvisioner.findOrCreate(subject,
                    jwt.getClaimAsString("username"));
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserUuid().toString());
            SecurityContextHolder.getContext().setAuthentication(createAuthentication(userDetails, request));
            log.debug("Authenticated auth subject {} as chat user {}", subject, user.getUserId());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token is not a valid auth-service access token: {}", e.getMessage());
            return false;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    private UsernamePasswordAuthenticationToken createAuthentication(
            UserDetails userDetails,
            HttpServletRequest request
    ) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        return authentication;
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/avatars/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/api-docs/")
                || path.equals("/swagger-ui.html");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
