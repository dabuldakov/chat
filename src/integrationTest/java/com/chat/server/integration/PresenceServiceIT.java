package com.chat.server.integration;

import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.contacts.ContactService;
import com.chat.server.exception.BadRequestException;
import com.chat.server.identity.PresenceInfo;
import com.chat.server.identity.PresenceService;
import com.chat.server.identity.PresenceSweeper;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверяет главное свойство presence: «онлайн» вычисляется из last_seen_at по TTL,
 * а не хранится во флаге. Именно это делает схему самовосстанавливающейся —
 * убитый клиент просто выпадает из окна TTL.
 */
class PresenceServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private PresenceSweeper presenceSweeper;

    @Autowired
    private ContactService contactService;

    @Autowired
    private UserService userService;

    private User user;
    private User contactUser;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("presence-user")
                .email("presence-user@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
        contactUser = userRepository.save(User.builder()
                .username("presence-contact")
                .email("presence-contact@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    /** Ставит last_seen_at напрямую, чтобы обойтись без сна на TTL-секунды. */
    private void backdateLastSeen(User target, LocalDateTime lastSeenAt) {
        User managed = userRepository.findById(target.getUserId()).orElseThrow();
        managed.setLastSeenAt(lastSeenAt);
        userRepository.save(managed);
        userRepository.flush();
    }

    @Test
    void shouldTreatUserWithoutActivityAsOffline() {
        PresenceInfo presence = presenceService.presenceOf(user.getUserId());

        assertThat(presence.online()).isFalse();
        assertThat(presence.lastSeenAt()).isNull();
    }

    @Test
    void shouldTreatFreshHeartbeatAsOnline() {
        presenceService.recordActivity(user.getUserId());

        assertThat(presenceService.presenceOf(user.getUserId()).online()).isTrue();
        assertThat(userRepository.findById(user.getUserId()).orElseThrow().getIsOnline()).isTrue();
    }

    @Test
    void shouldTreatActivityOlderThanTtlAsOffline() {
        backdateLastSeen(user, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10));

        PresenceInfo presence = presenceService.presenceOf(user.getUserId());

        assertThat(presence.online()).isFalse();
        // last_seen_at сохраняется: клиенту нужно «был в сети N минут назад».
        assertThat(presence.lastSeenAt()).isNotNull();
    }

    @Test
    void shouldIgnoreStuckIsOnlineFlagWhenLastSeenIsOld() {
        // Клиент убит без разлогина: флаг is_online остался true,
        // но активности давно не было.
        backdateLastSeen(user, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        User managed = userRepository.findById(user.getUserId()).orElseThrow();
        managed.setIsOnline(true);
        userRepository.save(managed);
        userRepository.flush();

        assertThat(presenceService.presenceOf(user.getUserId()).online())
                .as("залипший флаг не должен показывать неверный статус")
                .isFalse();
    }

    @Test
    void shouldKeepLastSeenAfterMarkOffline() {
        presenceService.recordActivity(user.getUserId());
        LocalDateTime seenAt = presenceService.presenceOf(user.getUserId()).lastSeenAt();
        assertThat(seenAt).isNotNull();

        presenceService.markOffline(user.getUserId());

        PresenceInfo presence = presenceService.presenceOf(user.getUserId());
        assertThat(presence.lastSeenAt()).isEqualTo(seenAt);
        assertThat(userRepository.findById(user.getUserId()).orElseThrow().getIsOnline()).isFalse();
    }

    @Test
    void shouldReturnBatchPresenceByUserIds() {
        presenceService.recordActivity(user.getUserId());

        Map<Long, PresenceInfo> presence = presenceService.presenceByUserIds(
                List.of(user.getUserId(), contactUser.getUserId()));

        assertThat(presence.get(user.getUserId()).online()).isTrue();
        assertThat(presence.get(contactUser.getUserId()).online()).isFalse();
        assertThat(presence.get(contactUser.getUserId()).lastSeenAt()).isNull();
    }

    @Test
    void shouldReturnBatchPresenceByUserUuids() {
        presenceService.recordActivity(contactUser.getUserId());

        Map<UUID, PresenceInfo> presence = presenceService.presenceByUserUuids(
                List.of(user.getUserUuid(), contactUser.getUserUuid()));

        assertThat(presence.get(contactUser.getUserUuid()).online()).isTrue();
        assertThat(presence.get(user.getUserUuid()).online()).isFalse();
    }

    @Test
    void shouldReturnEmptyBatchForBlankInput() {
        assertThat(presenceService.presenceByUserIds(List.of())).isEmpty();
        assertThat(presenceService.presenceByUserUuids(null)).isEmpty();
    }

    @Test
    void shouldSkipUnknownUserIdsInBatch() {
        Map<Long, PresenceInfo> presence = presenceService.presenceByUserIds(List.of(-1L));

        assertThat(presence).isEmpty();
    }

    @Test
    void shouldSweeperClearFlagButApiStatusStaysDerived() {
        backdateLastSeen(user, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        User managed = userRepository.findById(user.getUserId()).orElseThrow();
        managed.setIsOnline(true);
        userRepository.save(managed);
        userRepository.flush();

        presenceSweeper.expireStaleUsers();

        assertThat(userRepository.findById(user.getUserId()).orElseThrow().getIsOnline())
                .as("свипер чинит денормализованный флаг для индексов")
                .isFalse();
    }

    @Test
    void shouldExposeOnlineContactInContactList() {
        presenceService.recordActivity(contactUser.getUserId());
        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(contactUser.getUserUuid());
        contactService.addContact(user.getUserId(), request);

        var contacts = contactService.getUserContacts(user.getUserId());

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).isOnline())
                .as("контакт должен быть online после его heartbeat")
                .isTrue();
        assertThat(contacts.get(0).getLastSeenAt()).isNotNull();
    }

    @Test
    void shouldShowOfflineContactWithoutHeartbeat() {
        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(contactUser.getUserUuid());
        contactService.addContact(user.getUserId(), request);

        var contacts = contactService.getUserContacts(user.getUserId());

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).isOnline()).isFalse();
        assertThat(contacts.get(0).getLastSeenAt()).isNull();
    }

    /**
     * Ключевой регресс-тест на кэш.
     *
     * <p>Первый вызов кладёт сущность пользователя в 10-минутный кэш, где
     * last_seen_at ещё пустой. Heartbeat пишет напрямую в БД и кэш не чистит.
     * Если бы статус брался из сущности User, второй вызов увидел бы старый
     * кэш и показал «офлайн» ещё 10 минут — ровно тот баг, из-за которого
     * «в сети» не работало.
     */
    @Test
    void shouldReadPresenceFreshDespiteCachedUserEntity() {
        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(contactUser.getUserUuid());
        contactService.addContact(user.getUserId(), request);

        // Нагреваем кэш пользователей «офлайновой» сущностью: именно так вёл бы
        // себя код, читающий статус через getUserById. Heartbeat кэш не чистит.
        userService.getUserById(contactUser.getUserId());
        assertThat(contactService.getUserContacts(user.getUserId()).get(0).isOnline())
                .as("до heartbeat контакт офлайн")
                .isFalse();

        presenceService.recordActivity(contactUser.getUserId());

        var reloaded = contactService.getUserContacts(user.getUserId()).get(0);
        assertThat(reloaded.isOnline())
                .as("статус обязан обновляться, несмотря на кэш пользователей")
                .isTrue();
        assertThat(reloaded.getLastSeenAt()).isNotNull();
    }

    @Test
    void shouldParseCommaSeparatedAndRepeatedUuids() {
        UUID a = user.getUserUuid();
        UUID b = contactUser.getUserUuid();

        List<UUID> parsed = presenceService.parseUserUuids(
                List.of(a + "," + b, a.toString(), "  " + b + "  "));

        assertThat(parsed).containsExactly(a, b);
    }

    @Test
    void shouldDiscardInvalidUuidsInsteadOfFailingRequest() {
        UUID valid = user.getUserUuid();

        List<UUID> parsed = presenceService.parseUserUuids(
                List.of(valid + ",not-a-uuid,,  ,also-bad"));

        assertThat(parsed).containsExactly(valid);
    }

    @Test
    void shouldRejectBatchOverLimit() {
        List<String> many = java.util.stream.IntStream.range(0, 201)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();

        assertThatThrownBy(() -> presenceService.parseUserUuids(many))
                .isInstanceOf(BadRequestException.class);
    }
}