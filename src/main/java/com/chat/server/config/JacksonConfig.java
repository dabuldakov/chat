package com.chat.server.config;

import com.chat.server.util.IsoUtc;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.chat.server.common.BaseEntity;
/**
 * Единый формат времени в ответах API: все {@link LocalDateTime} отдаются
 * как UTC-строка с суффиксом {@code Z} (например {@code 2026-09-22T12:00:00Z}).
 *
 * Хранящиеся в БД значения трактуются как UTC (см. {@code BaseEntity});
 * клиенты пересчитывают их в свой часовой пояс. Входящие строки с {@code Z}
 * и обычные ISO-строки десериализуются одинаково (время без зоны считается UTC).
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter UTC_WITH_Z =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    public JsonMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        return builder -> builder.addModule(buildUtcModule());
    }

    static SimpleModule buildUtcModule() {
        SimpleModule module = new SimpleModule("localdatetime-utc", Version.unknownVersion());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(UTC_WITH_Z));
        module.addDeserializer(LocalDateTime.class, new ValueDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
                return IsoUtc.parse(p.getValueAsString());
            }
        });
        return module;
    }
}