package com.chat.server.identity;

import com.chat.server.exception.BadRequestException;
import com.chat.server.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Единственное место, где решается, онлайн ли пользователь.
 *
 * <p><b>Почему online вычисляется, а не хранится.</b> Флаг {@code is_online} в
 * БД — источник двух типовых проблем: клиент, убитый без разлогина, остаётся
 * «онлайн» навсегда, а разлогин на одном устройстве гасит статус на всех.
 * Вместо этого источник истины — {@code last_seen_at}: клиент шлёт heartbeat,
 * а «онлайн» означает «heartbeat был недавно»
 * ({@link PresenceProperties#getOnlineTtlSeconds()}).
 * Схема самовосстанавливается: пропавший клиент просто выпадает из окна TTL.
 *
 * <p>Флаг {@code is_online} при этом остаётся — как денормализованное поле для
 * индексированных выборок; его поддерживают heartbeat и {@link PresenceSweeper}.
 * Но ответы клиенту считаются всегда из {@code last_seen_at}, поэтому «залипший»
 * флаг не может показать неверный статус.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final UserRepository userRepository;
    private final PresenceProperties properties;

    // ==================== Запись активности ====================

    /**
     * Heartbeat: «я жив, приложение на переднем плане».
     *
     * <p>Один UPDATE без чтения сущности: heartbeat идёт каждые 15 секунд от
     * каждого активного пользователя, и лишний SELECT в этом пути заметен.
     */
    @Transactional
    public void recordActivity(Long userId) {
        if (userRepository.touchPresence(userId, LocalDateTime.now(ZoneOffset.UTC)) == 0) {
            log.debug("Heartbeat ignored: user {} not found or deleted", userId);
        }
    }

    /**
     * Явный уход: снимает флаг «онлайн». last_seen_at сохраняется — по нему
     * клиент покажет «был в сети N минут назад».
     */
    @Transactional
    public void markOffline(Long userId) {
        userRepository.clearPresence(userId);
    }

    // ==================== Чтение ====================

    /** «Онлайн» = последняя активность была недавно. Единственное правило. */
    public boolean isOnline(LocalDateTime lastSeenAt) {
        return lastSeenAt != null
                && lastSeenAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).minus(properties.onlineTtl()));
    }

    /** Пересчитывает статус для уже загруженного пользователя. */
    public PresenceInfo presenceOf(LocalDateTime lastSeenAt) {
        return new PresenceInfo(isOnline(lastSeenAt), lastSeenAt);
    }

    /**
     * Статус одного пользователя. Читает прямиком из БД, минуя кэш
     * пользователей: запись в кэше живёт 10 минут, а статус «в сети» должен
     * быть актуальным.
     */
    @Transactional(readOnly = true)
    public PresenceInfo presenceOf(Long userId) {
        return presenceByUserIds(List.of(userId)).getOrDefault(userId, PresenceInfo.unknown());
    }

    /** Пакетный статус по внутренним id — для списков контактов и участников. */
    @Transactional(readOnly = true)
    public Map<Long, PresenceInfo> presenceByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return collect(userRepository.findPresenceByUserIds(userIds), PresenceProjection::getUserId);
    }

    /** Пакетный статус по UUID — для запроса batch-presence от клиента. */
    @Transactional(readOnly = true)
    public Map<UUID, PresenceInfo> presenceByUserUuids(Collection<UUID> userUuids) {
        if (userUuids == null || userUuids.isEmpty()) {
            return Collections.emptyMap();
        }
        return collect(userRepository.findPresenceByUserUuids(userUuids), PresenceProjection::getUserUuid);
    }

    private <K> Map<K, PresenceInfo> collect(List<PresenceProjection> rows,
                                             Function<PresenceProjection, K> keyExtractor) {
        Map<K, PresenceInfo> result = new LinkedHashMap<>();
        for (PresenceProjection row : rows) {
            result.put(keyExtractor.apply(row), presenceOf(row.getLastSeenAt()));
        }
        return result;
    }

    /**
     * UUID из query-параметра: значения приходят списком (и могут содержать
     * запятые), пустые и невалидные отбрасываем молча, чтобы один битый UUID
     * не ломал весь запрос статусов.
     */
    public List<UUID> parseUserUuids(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<UUID> parsed = raw.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (parsed.size() > properties.getMaxBatchSize()) {
            throw new BadRequestException(
                    "Too many user UUIDs: at most " + properties.getMaxBatchSize() + " allowed");
        }
        return parsed;
    }
}
