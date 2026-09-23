package com.chat.server.integration;

import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.contacts.ContactDto;
import com.chat.server.user.User;
import com.chat.server.exception.ConflictException;
import com.chat.server.user.UserRepository;
import com.chat.server.contacts.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactService contactService;

    private User user;
    private User contactUser;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("user")
                .email("user@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
        contactUser = userRepository.save(User.builder()
                .username("contact")
                .email("contact@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    private AddContactRequestDto addContactRequest(User contact) {
        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(contact.getUserUuid());
        return request;
    }

    @Test
    void shouldAddContact() {
        var contact = contactService.addContact(user.getUserId(), addContactRequest(contactUser));

        assertThat(contact.getContactId()).isNotNull();
        assertThat(contact.getUserId()).isEqualTo(user.getUserId());
        assertThat(contact.getContactUserId()).isEqualTo(contactUser.getUserId());
        assertThat(contactService.isContact(user.getUserId(), contactUser.getUserId())).isTrue();
    }

    @Test
    void shouldReturnAllContactsForUser() {
        contactService.addContact(user.getUserId(), addContactRequest(contactUser));

        List<ContactDto> contacts = contactService.getUserContacts(user.getUserId());

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getContactUserUuid()).isEqualTo(contactUser.getUserUuid());
        assertThat(contacts.get(0).getUsername()).isEqualTo(contactUser.getUsername());
    }

    @Test
    void shouldRejectAddingYourselfAsContact() {
        assertThatThrownBy(() -> contactService.addContact(user.getUserId(), addContactRequest(user)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldRejectDuplicateContact() {
        contactService.addContact(user.getUserId(), addContactRequest(contactUser));

        assertThatThrownBy(() -> contactService.addContact(user.getUserId(), addContactRequest(contactUser)))
                .isInstanceOf(ConflictException.class);
        assertThat(contactService.getUserContacts(user.getUserId())).hasSize(1);
    }

    @Test
    void shouldRemoveContact() {
        var contact = contactService.addContact(user.getUserId(), addContactRequest(contactUser));

        contactService.removeContact(user.getUserId(), contact.getContactUuid());

        assertThat(contactService.getUserContacts(user.getUserId())).isEmpty();
        assertThat(contactService.isContact(user.getUserId(), contactUser.getUserId())).isFalse();
    }
}