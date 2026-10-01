package com.chat.server.presence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Presence API: клиент сообщает о себе и спрашивает о других.
 *
 * <p>Два вызова покрывают всю задачу индикатора «в сети»:
 * <ul>
 *   <li>{@code POST /api/presence/heartbeat} — «я на переднем плане», продлевает окно онлайна;</li>
 *   <li>{@code GET /api/presence} — пакетный статус для списка контактов.</li>
 * </ul>
 * Heartbeat и batch-статус — разные запросы намеренно: первый меняет состояние
 * и идёт раз в 15 секунд, второй только читает и нужен лишь пока открыт
 * список контактов.
 */
@Slf4j
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Tag(name = "Presence", description = "Статус присутствия («в сети»)")
public class PresenceController {

    private final PresenceService presenceService;

    @PostMapping("/heartbeat")
    @Operation(summary = "Отметить активность: продлевает статус «в сети»")
    public ResponseEntity<Void> heartbeat(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        presenceService.recordActivity(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Пакетный статус присутствия пользователей по их UUID")
    public ResponseEntity<List<PresenceResponse>> getPresence(
            @RequestParam(name = "userUuids", required = false) List<String> userUuids) {
        List<UUID> uuids = presenceService.parseUserUuids(userUuids);

        // Неизвестные UUID не ошибка: клиент мог увидеть контакт, удалённого
        // другим пользователем. Такие просто не попадают в ответ.
        Map<UUID, PresenceInfo> presence = presenceService.presenceByUserUuids(uuids);

        List<PresenceResponse> body = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            body.add(PresenceResponse.of(uuid, presence.getOrDefault(uuid, PresenceInfo.unknown())));
        }
        return ResponseEntity.ok(body);
    }
}
