package com.chat.server.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badRequestMapsTo400() {
        assertStatus(handler.handleBadRequest(new BadRequestException("bad")), HttpStatus.BAD_REQUEST, "bad");
    }

    @Test
    void conflictMapsTo409() {
        assertStatus(handler.handleConflict(new ConflictException("dup")), HttpStatus.CONFLICT, "dup");
    }

    @Test
    void unauthorizedMapsTo401() {
        assertStatus(handler.handleUnauthorized(new UnauthorizedException("nope")), HttpStatus.UNAUTHORIZED, "nope");
    }

    @Test
    void accessDeniedMapsTo403() {
        assertStatus(handler.handleAccessDenied(new AccessDeniedException("no")), HttpStatus.FORBIDDEN, "no");
    }

    @Test
    void notFoundMapsTo404() {
        assertStatus(handler.handleNotFound(new NotFoundException("missing")), HttpStatus.NOT_FOUND, "missing");
    }

    @Test
    void genericMapsTo500() {
        assertStatus(handler.handleGeneric(new RuntimeException("boom")), HttpStatus.INTERNAL_SERVER_ERROR, "boom");
    }

    private void assertStatus(ResponseEntity<?> response, HttpStatus expected, String message) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo(message);
    }
}
