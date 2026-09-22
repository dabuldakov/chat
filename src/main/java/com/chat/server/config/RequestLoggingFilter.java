package com.chat.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Логирует каждый HTTP-запрос: метод, путь, статус, длительность, пользователя
 * и request_id. Проставляет userId и request_id в MDC, чтобы они были отдельными
 * полями в структурных (JSON) логах и по ним можно было искать в Grafana/Loki.
 * Ставится ПОСЛЕ JwtAuthenticationFilter, чтобы в SecurityContext уже был юзер.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Пропускаем preflight-запросы CORS
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String user = currentUser();

        MDC.put("request_id", requestId);
        MDC.put("user", user);

        long start = System.currentTimeMillis();
        long duration;
        try {
            chain.doFilter(request, response);
            duration = System.currentTimeMillis() - start;
            try {
                log.info("HTTP {} {} -> {} ({}ms, user={}, ua={}, request_id={})",
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        duration, user, request.getHeader("User-Agent"), requestId);
            } finally {
                clearMdc();
            }
        } catch (IOException | ServletException | RuntimeException e) {
            duration = System.currentTimeMillis() - start;
            try {
                log.error("HTTP {} {} -> ERROR after {}ms (user={}, ua={}, request_id={})",
                        request.getMethod(), request.getRequestURI(), duration, user,
                        request.getHeader("User-Agent"), requestId, e);
            } finally {
                clearMdc();
            }
            throw e;
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String name = auth.getName();
            if (name != null && !"anonymousUser".equals(name)) {
                return name;
            }
        }
        return "-";
    }

    private void clearMdc() {
        MDC.remove("request_id");
        MDC.remove("user");
    }
}