package com.chat.server.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReportRequestDto {

    @NotNull(message = "contentType is required")
    private ContentReport.ContentType contentType;

    @NotNull(message = "contentUuid is required")
    private UUID contentUuid;

    @NotNull(message = "reason is required")
    private ContentReport.Reason reason;

    @Size(max = 1000, message = "comment must be at most 1000 characters")
    private String comment;
}
