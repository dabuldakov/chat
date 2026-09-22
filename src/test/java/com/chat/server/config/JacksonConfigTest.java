package com.chat.server.config;

import com.chat.server.util.IsoUtc;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что LocalDateTime сериализуется в UTC с суффиксом {@code Z},
 * а десериализация принимает и такие строки, и обычный ISO-формат без зоны.
 */
class JacksonConfigTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(JacksonConfig.buildUtcModule())
            .build();

    @Test
    void localDateTimeSerializesAsUtcWithZ() throws Exception {
        String json = objectMapper.writeValueAsString(new TimeHolder(LocalDateTime.of(2026, 9, 22, 12, 0, 0)));
        assertThat(json).contains("2026-09-22T12:00:00Z");
    }

    @Test
    void localDateTimeDeserializesZAndPlainIso() throws Exception {
        LocalDateTime fromZ = objectMapper.readValue("{\"createdAt\":\"2026-09-22T12:00:00Z\"}", TimeHolder.class).createdAt();
        LocalDateTime fromPlain = objectMapper.readValue("{\"createdAt\":\"2026-09-22T12:00:00\"}", TimeHolder.class).createdAt();

        assertThat(fromZ).isEqualTo(LocalDateTime.of(2026, 9, 22, 12, 0));
        assertThat(fromPlain).isEqualTo(LocalDateTime.of(2026, 9, 22, 12, 0));
    }

    @Test
    void isoUtcParsesPlainZAndFractionalFormats() {
        assertThat(IsoUtc.parse("2026-09-22T12:00:00")).isEqualTo(LocalDateTime.of(2026, 9, 22, 12, 0));
        assertThat(IsoUtc.parse("2026-09-22T12:00:00Z")).isEqualTo(LocalDateTime.of(2026, 9, 22, 12, 0));
        assertThat(IsoUtc.parse("2026-09-22T12:00:00.123456789Z"))
                .isEqualTo(LocalDateTime.of(2026, 9, 22, 12, 0, 0, 123456789));
    }

    private record TimeHolder(LocalDateTime createdAt) {
    }
}