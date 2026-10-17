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
 * Аутентификация по Bearer-токену. Поддерживает два источника:
 * <ol>
 *   <li>access-токен единого IdP (auth-service, RS256) — проверяется по JWKS,
 *       пользователь находится/создаётся по claim {@code sub};</li>
 *   <li>legacy HS256-токен chat (собственный логин/пароль) — на время миграции.</li>
 * </ol>
 * В обоих случаях в SecurityContext кладётся principal, у которого
 * {@code getName()} — числовой {@code users.user_id}, как и раньше.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final OidcUserProvisioner oidcUserProvisioner;
    private final OidcProperties oidcProperties;
    private final ObjectProvider<JwtDecoder> oidcDecoderProvider;

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
                if (!authenticateWithOidc(token, request)) {
                    authenticateWithLegacyToken(token, request);
                }
            }
        } catch (Exception e) {
            log.warn("Authentication error for request {}: {}", requestPath, e.getMessage());
        }

        filterChain.doFilter(request, response);
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
                    jwt.getClaimAsString("username"), jwt.getClaimAsString("email"));
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserUuid().toString());
            SecurityContextHolder.getContext().setAuthentication(createAuthentication(userDetails, request));
            log.debug("Authenticated auth subject {} as chat user {}", subject, user.getUserId());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token is not a valid auth-service access token: {}", e.getMessage());
            return false;
        }
    }

    /** Legacy HS256-токен, выданный самим chat. */
    private void authenticateWithLegacyToken(String token, HttpServletRequest request) {
        if (!jwtUtil.validateToken(token)) {
            return;
        }
        UUID userUuid = jwtUtil.getUserIdFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(userUuid.toString());
        SecurityContextHolder.getContext().setAuthentication(createAuthentication(userDetails, request));
        log.debug("Authenticated legacy chat user: {}", userUuid);
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
        // ВАЖНО: /api/auth/** сюда не входит. Часть эндпоинтов (/api/auth/me,
        // /api/auth/logout*, /api/auth/change-password) требует Authentication,
        // поэтому JWT-фильтр обязан их обрабатывать. Публичность конкретных
        // auth-эндпоинтов (login/register/refresh/...) задаётся в SecurityConfig.
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
