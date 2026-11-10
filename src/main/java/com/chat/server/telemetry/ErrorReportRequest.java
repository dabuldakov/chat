package com.chat.server.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Батч ошибок от клиента. */
@Data
public class ErrorReportRequest {

    @NotEmpty
    @Size(max = 100)
    private List<@Valid ErrorEventDto> events;

    /** Клиентский буфер достиг лимита: часть ошибок потеряна. */
    private boolean overflowed;

    @Data
    public static class ErrorEventDto {
        @NotBlank
        private String eventId;
        private String time;
        @NotBlank
        private String level;
        private String tag;
        private String message;
        private String stack;
        @NotBlank
        private String appVersion;
        private Integer versionCode;
        private String buildType;
        private String device;
        private String os;
        private String userId;
    }
}
