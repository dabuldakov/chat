package com.chat.server.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Жалобы на пользовательский контент")
public class ContentReportController {

    private final ContentReportService reportService;

    @PostMapping
    @Operation(summary = "Пожаловаться на сообщение, пользователя или чат")
    public ResponseEntity<ContentReportDto> createReport(
            @Valid @RequestBody CreateReportRequestDto request,
            Authentication authentication) {
        Long reporterId = Long.parseLong(authentication.getName());
        ContentReport report = reportService.createReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ContentReportDto.fromEntity(report));
    }
}
