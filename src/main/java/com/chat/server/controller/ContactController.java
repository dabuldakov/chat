package com.chat.server.controller;

import com.chat.server.dto.request.AddContactRequestDto;
import com.chat.server.dto.response.ContactDto;
import com.chat.server.entity.Contact;
import com.chat.server.entity.User;
import com.chat.server.service.ContactService;
import com.chat.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts", description = "API для управления контактами")
public class ContactController {

    private final ContactService contactService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Получение всех контактов пользователя")
    public ResponseEntity<List<ContactDto>> getContacts(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<ContactDto> contacts = contactService.getUserContacts(userId);
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/page")
    @Operation(summary = "Получение контактов с пагинацией")
    public ResponseEntity<Page<ContactDto>> getContactsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Page<ContactDto> contacts = contactService.getUserContactsPaginated(
                userId, PageRequest.of(page, size, Sort.by("contactName").ascending())
        );
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск контактов")
    public ResponseEntity<Page<ContactDto>> searchContacts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Page<ContactDto> contacts = contactService.searchContacts(
                userId, query, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/{contactUuid}")
    @Operation(summary = "Получение контакта по UUID")
    public ResponseEntity<ContactDto> getContactByUuid(
            @PathVariable UUID contactUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        ContactDto contact = contactService.getContactByUuid(userId, contactUuid);
        return ResponseEntity.ok(contact);
    }

    @PostMapping
    @Operation(summary = "Добавление контакта")
    public ResponseEntity<ContactDto> addContact(
            @Valid @RequestBody AddContactRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Contact contact = contactService.addContact(userId, request);

        User contactUser = userService.getUserById(contact.getContactUserId());
        return ResponseEntity.ok(ContactDto.fromEntity(contact, contactUser));
    }

    @DeleteMapping("/{contactUuid}")
    @Operation(summary = "Удаление контакта")
    public ResponseEntity<Void> removeContact(
            @PathVariable UUID contactUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        contactService.removeContact(userId, contactUuid);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{contactUuid}/name")
    @Operation(summary = "Обновление имени контакта")
    public ResponseEntity<Void> updateContactName(
            @PathVariable UUID contactUuid,
            @RequestParam String name,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        contactService.updateContactName(userId, contactUuid, name);
        return ResponseEntity.ok().build();
    }
}