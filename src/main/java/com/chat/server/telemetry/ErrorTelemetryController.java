package com.chat.server.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Приём диагностики клиента. Ошибки приходят батчем при старте приложения,
 * дубликаты по eventId отбрасываются (клиент может повторить отправку).
 */
@Slf4j
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class ErrorTelemetryController {

    private final ErrorEventRepository repository;

    @PostMapping("/errors")
    public ResponseEntity<Map<String, Integer>> upload(
            @Valid @RequestBody ErrorReportRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        int accepted = 0;
        for (ErrorReportRequest.ErrorEventDto dto : request.getEvents()) {
            UUID eventId;
            try {
                eventId = UUID.fromString(dto.getEventId());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (repository.existsByEventId(eventId)) {
                continue;
            }
            repository.save(ErrorEvent.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .appVersion(limit(dto.getAppVersion(), 32))
                    .versionCode(dto.getVersionCode())
                    .buildType(limit(dto.getBuildType(), 16))
                    .device(limit(dto.getDevice(), 200))
                    .os(limit(dto.getOs(), 64))
                    .level(limit(dto.getLevel(), 16))
                    .tag(limit(dto.getTag(), 200))
                    .message(limit(dto.getMessage(), 1_000))
                    .stack(limit(dto.getStack(), 20_000))
                    .eventTime(parseTime(dto.getTime()))
                    .build());
            accepted++;
        }
        return ResponseEntity.ok(Map.of("accepted", accepted));
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
