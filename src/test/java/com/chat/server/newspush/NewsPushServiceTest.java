package com.chat.server.newspush;

import com.chat.server.contacts.ContactRepository;
import com.chat.server.exception.NotFoundException;
import com.chat.server.identity.DeviceTokenRepository;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import com.chat.server.notification.FcmService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Выбор получателей push о новости: автор не найден, нет контактов,
 * дедупликация токенов и fallback имени автора на username.
 */
class NewsPushServiceTest {

    private final DeviceTokenRepository tokens = mock(DeviceTokenRepository.class);
    private final UserService users = mock(UserService.class);
    private final ContactRepository contacts = mock(ContactRepository.class);
    private final FcmService fcm = mock(FcmService.class);
    private final NewsPushService service = new NewsPushService(tokens, users, contacts, fcm);

    private User author(String firstName, String lastName) {
        return User.builder()
                .userId(7L)
                .username("alice")
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }

    @Test
    void authorNotFoundSkipsEverything() {
        when(users.findByUsername("alice")).thenThrow(new NotFoundException("no user"));

        service.sendNewsNotification("alice", 1L, "title", "body");

        verifyNoInteractions(contacts, tokens, fcm);
    }

    @Test
    void noContactsSkipsPush() {
        when(users.findByUsername("alice")).thenReturn(author("Alice", "A"));
        when(contacts.findUserIdsByContactUserId(7L)).thenReturn(List.of(7L));

        service.sendNewsNotification("alice", 1L, "title", "body");

        verifyNoInteractions(tokens, fcm);
    }

    @Test
    void sendsOneNotificationPerDistinctNonEmptyToken() {
        when(users.findByUsername("alice")).thenReturn(author("Alice", "A"));
        when(contacts.findUserIdsByContactUserId(7L)).thenReturn(List.of(8L, 9L, 7L));
        when(tokens.findFcmTokensByUserIds(List.of(8L, 9L)))
                .thenReturn(List.of("t1", "t1", "", "t2"));

        service.sendNewsNotification("alice", 42L, "Title", "Body");

        verify(fcm).sendNewsNotification("t1", 42L, "Title", "Body", "Alice A");
        verify(fcm).sendNewsNotification("t2", 42L, "Title", "Body", "Alice A");
        verifyNoMoreInteractions(fcm);
    }

    @Test
    void blankFullNameFallsBackToUsername() {
        when(users.findByUsername("alice")).thenReturn(author(null, null));
        when(contacts.findUserIdsByContactUserId(7L)).thenReturn(List.of(8L));
        when(tokens.findFcmTokensByUserIds(List.of(8L))).thenReturn(List.of("t1"));

        service.sendNewsNotification("alice", 1L, "t", "b");

        verify(fcm).sendNewsNotification("t1", 1L, "t", "b", "alice");
    }
}
