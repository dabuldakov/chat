package com.chat.server.presence;

import com.chat.server.chat.Participant;
import com.chat.server.chat.ParticipantInfoDto;
import com.chat.server.contacts.Contact;
import com.chat.server.contacts.ContactDto;
import com.chat.server.user.User;
import com.chat.server.user.UserDto;
import com.chat.server.user.UserProfileDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт presence между бэкендом и клиентом.
 *
 * <p>Регрессия, которую этот тест закрывает: Lombok генерирует геттер
 * {@code isOnline()}, а Jackson срезает префикс {@code is} и отдаёт ключ
 * {@code "online"}. Клиент при этом ждал {@code "isOnline"} и десериализовывал
 * значение в примитив {@code boolean}, то есть молча получал {@code false} —
 * «в сети» не показывалось ни у кого, без единой ошибки в логах.
 *
 * <p>Имена ключей зафиксированы явно и проверяются здесь, иначе следующий DTO с
 * полем {@code isSomething} снова разъедется с клиентом. Формат дат проверяет
 * JacksonConfigTest — здесь важно только имя ключа и обязательность поля.
 */
class PresenceJsonContractTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final User USER = User.builder()
            .userId(7L)
            .userUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .username("alice")
            .firstName("Alice")
            .lastName("Smith")
            .email("alice@example.com")
            .passwordHash("hash")
            .emailVerified(true)
            .build();

    private static final PresenceInfo ONLINE =
            new PresenceInfo(true, LocalDateTime.of(2026, 9, 22, 12, 0));

    @Test
    void contactDtoUsesOnlineKey() throws Exception {
        Contact contact = Contact.builder()
                .contactId(1L)
                .contactUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .userId(7L)
                .contactUserId(9L)
                .createdAt(LocalDateTime.of(2026, 9, 22, 10, 0))
                .build();

        String json = objectMapper.writeValueAsString(ContactDto.fromEntity(contact, USER, ONLINE));

        assertThat(json).contains("\"online\":true");
        assertThat(json).doesNotContain("isOnline");
    }

    @Test
    void userDtoUsesOnlineKey() throws Exception {
        String json = objectMapper.writeValueAsString(UserDto.fromEntity(USER, ONLINE));

        assertThat(json).contains("\"online\":true");
        assertThat(json).doesNotContain("isOnline");
    }

    @Test
    void userProfileDtoUsesOnlineKeyAndExposesLastSeen() throws Exception {
        String json = objectMapper.writeValueAsString(UserProfileDto.fromEntity(USER, ONLINE));

        assertThat(json).contains("\"online\":true");
        assertThat(json).contains("\"lastSeenAt\"");
        assertThat(json).doesNotContain("isOnline");
    }

    @Test
    void participantInfoDtoUsesOnlineKey() throws Exception {
        Participant participant = new Participant();
        participant.setRole(Participant.ParticipantRole.MEMBER);
        participant.setJoinedAt(LocalDateTime.of(2026, 9, 22, 10, 0));

        String json = objectMapper.writeValueAsString(
                ParticipantInfoDto.fromEntity(participant, USER, ONLINE));

        assertThat(json).contains("\"online\":true");
        assertThat(json).doesNotContain("isOnline");
    }

    @Test
    void presenceResponseUsesOnlineKey() throws Exception {
        String json = objectMapper.writeValueAsString(
                PresenceResponse.of(USER.getUserUuid(), ONLINE));

        assertThat(json).contains("\"online\":true");
        assertThat(json).contains("\"lastSeenAt\"");
        assertThat(json).doesNotContain("isOnline");
    }

    @Test
    void offlineStatusIsExplicitFalseNotMissingField() throws Exception {
        // Клиент различает «офлайн» и «сервер не ответил»: поле обязано приходить
        // всегда, иначе неотвеченный запрос выглядел бы как «офлайн».
        String json = objectMapper.writeValueAsString(
                UserProfileDto.fromEntity(USER, PresenceInfo.unknown()));

        assertThat(json).contains("\"online\":false");
        assertThat(json).contains("\"lastSeenAt\":null");
    }
}