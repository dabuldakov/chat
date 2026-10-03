package com.chat.server.contacts;

import com.chat.server.identity.User;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.identity.PresenceInfo;
import com.chat.server.identity.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.chat.server.identity.UserService;
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserService userService;
    private final PresenceService presenceService;

    @Transactional
    public Contact addContact(Long userId, AddContactRequestDto request) {
        log.info("Adding contact for user: {}", userId);

        User user = userService.getUserById(userId);
        User contactUser = userService.getUserByUuid(request.getContactUserUuid());

        // Нельзя добавить себя в контакты
        if (userId.equals(contactUser.getUserId())) {
            throw new ConflictException("Cannot add yourself as a contact");
        }

        // Проверяем, не добавлен ли уже
        if (contactRepository.existsByUserIdAndContactUserId(userId, contactUser.getUserId())) {
            throw new ConflictException("Contact already exists");
        }

        Contact contact = Contact.builder()
                .userId(userId)
                .contactUserId(contactUser.getUserId())
                .contactName(request.getContactName() != null ? request.getContactName() : contactUser.getUsername())
                .build();

        Contact savedContact = contactRepository.save(contact);
        log.info("Contact added: {} -> {}", userId, contactUser.getUserId());

        return savedContact;
    }

    @Transactional(readOnly = true)
    public List<ContactDto> getUserContacts(Long userId) {
        log.debug("Fetching contacts for user: {}", userId);

        userService.getUserById(userId);

        List<Contact> contacts = contactRepository.findByUserIdOrderByName(userId);

        return toDtos(contacts);
    }

    @Transactional(readOnly = true)
    public Page<ContactDto> getUserContactsPaginated(Long userId, Pageable pageable) {
        log.debug("Fetching contacts for user: {} with pagination", userId);

        userService.getUserById(userId);

        Page<Contact> contacts = contactRepository.findByUserId(userId, pageable);
        return new PageImpl<>(toDtos(contacts.getContent()), pageable, contacts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<ContactDto> searchContacts(Long userId, String search, Pageable pageable) {
        log.debug("Searching contacts for user: {} with query: {}", userId, search);

        userService.getUserById(userId);

        Page<Contact> contacts = contactRepository.searchContacts(userId, search, pageable);
        return new PageImpl<>(toDtos(contacts.getContent()), pageable, contacts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ContactDto getContactByUuid(Long userId, UUID contactUuid) {
        log.debug("Getting contact by UUID: {} for user: {}", contactUuid, userId);

        Contact contact = contactRepository.findByContactUuid(contactUuid)
                .orElseThrow(() -> new NotFoundException("Contact not found"));

        // Проверяем, что контакт принадлежит пользователю
        if (!contact.getUserId().equals(userId)) {
            throw new NotFoundException("Contact not found");
        }

        return toDtos(List.of(contact)).get(0);
    }

    /**
     * Собирает DTO контактов, подгружая пользователей и их присутствие.
     *
     * <p>Два пакетных запроса вместо запроса на контакт: пользователи — через
     * findAllByIds, статус — через {@link PresenceService#presenceByUserIds}.
     *
     * <p>Статус запрашивается отдельным запросом намеренно. Кэш пользователей
     * живёт 10 минут, и если взять last_seen_at из сущности User, свежесть
     * статуса окажется связана с тем, как закэширован именно этот путь.
     * Отдельный запрос к PresenceService такого допустить не может: он всегда
     * читает актуальный last_seen_at и пересчитывает «онлайн» по TTL.
     */
    private List<ContactDto> toDtos(List<Contact> contacts) {
        if (contacts.isEmpty()) {
            return List.of();
        }

        List<Long> contactUserIds = contacts.stream()
                .map(Contact::getContactUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, User> usersById = userService.findAllByIds(contactUserIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, PresenceInfo> presenceByUserId = presenceService.presenceByUserIds(contactUserIds);

        return contacts.stream()
                .map(contact -> {
                    Long contactUserId = contact.getContactUserId();
                    User contactUser = usersById.get(contactUserId);
                    PresenceInfo presence = presenceByUserId.getOrDefault(
                            contactUserId, PresenceInfo.unknown());
                    return ContactDto.fromEntity(contact, contactUser, presence);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isContact(Long userId, Long contactUserId) {
        return contactRepository.existsByUserIdAndContactUserId(userId, contactUserId);
    }

    @Transactional
    public void removeContact(Long userId, UUID contactUuid) {
        log.info("Removing contact for user: {}, contact UUID: {}", userId, contactUuid);

        Contact contact = contactRepository.findByContactUuid(contactUuid)
                .orElseThrow(() -> new NotFoundException("Contact not found"));

        if (!contact.getUserId().equals(userId)) {
            throw new NotFoundException("Contact not found");
        }

        contactRepository.delete(contact);
        log.info("Contact removed: {} -> {}", userId, contact.getContactUserId());
    }

    @Transactional
    public void removeContactByUserId(Long userId, Long contactUserId) {
        log.info("Removing contact for user: {}, contact user: {}", userId, contactUserId);

        contactRepository.deleteByUserIdAndContactUserId(userId, contactUserId);
        log.info("Contact removed: {} -> {}", userId, contactUserId);
    }

    @Transactional
    public void removeAllContacts(Long userId) {
        log.warn("Removing all contacts for user: {}", userId);
        contactRepository.deleteByUserId(userId);
        log.info("All contacts removed for user: {}", userId);
    }

    @Transactional(readOnly = true)
    public List<Long> getContactUserIds(Long userId) {
        return contactRepository.findContactUserIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Long> getExistingContactIds(Long userId, List<Long> userIds) {
        return contactRepository.findExistingContactIds(userId, userIds);
    }

    @Transactional
    public void updateContactName(Long userId, UUID contactUuid, String newName) {
        log.info("Updating contact name for user: {}, contact: {}", userId, contactUuid);

        Contact contact = contactRepository.findByContactUuid(contactUuid)
                .orElseThrow(() -> new NotFoundException("Contact not found"));

        if (!contact.getUserId().equals(userId)) {
            throw new NotFoundException("Contact not found");
        }

        contact.setContactName(newName);
        contactRepository.save(contact);
        log.info("Contact name updated: {} -> {}", contactUuid, newName);
    }
}