package com.chat.server.identity;

import com.chat.server.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PresenceServiceTest {

    private final PresenceProperties properties = new PresenceProperties();
    private final PresenceService service = new PresenceService(mock(UserRepository.class), properties);

    @Test
    void onlineWhenLastSeenWithinTtl() {
        assertThat(service.isOnline(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10))).isTrue();
    }

    @Test
    void offlineWhenLastSeenOutsideTtl() {
        assertThat(service.isOnline(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(600))).isFalse();
    }

    @Test
    void nullLastSeenIsOffline() {
        assertThat(service.isOnline(null)).isFalse();
        assertThat(service.presenceOf((LocalDateTime) null).online()).isFalse();
    }

    @Test
    void parsesCommaSeparatedAndDeduplicatesUuids() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<UUID> parsed = service.parseUserUuids(List.of(first + "," + second, first.toString(), "  "));

        assertThat(parsed).containsExactly(first, second);
    }

    @Test
    void dropsMalformedUuidsWithoutFailing() {
        UUID valid = UUID.randomUUID();

        assertThat(service.parseUserUuids(List.of("not-a-uuid," + valid))).containsExactly(valid);
    }

    @Test
    void rejectsTooManyUuids() {
        properties.setMaxBatchSize(1);

        assertThatThrownBy(() -> service.parseUserUuids(List.of(UUID.randomUUID() + "," + UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class);
    }
}
