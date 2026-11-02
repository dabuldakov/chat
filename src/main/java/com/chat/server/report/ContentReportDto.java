package com.chat.server.report;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class ContentReportDto {

    UUID reportUuid;
    ContentReport.ContentType contentType;
    ContentReport.Status status;
    LocalDateTime createdAt;

    public static ContentReportDto fromEntity(ContentReport report) {
        return ContentReportDto.builder()
                .reportUuid(report.getReportUuid())
                .contentType(report.getContentType())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
