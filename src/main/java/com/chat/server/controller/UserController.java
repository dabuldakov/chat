package com.chat.server.controller;

import com.chat.server.dto.request.UpdateProfileRequestDto;
import com.chat.server.dto.response.UserDto;
import com.chat.server.dto.response.UserProfileDto;
import com.chat.server.entity.User;
import com.chat.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API для управления пользователями")
public class UserController {

    private final UserService userService;
    private final FileUploadService fileUploadService;

    @GetMapping("/me")
    @Operation(summary = "Получение своего профиля")
    public ResponseEntity<UserProfileDto> getMyProfile(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(UserProfileDto.fromEntity(user));
    }

    @PutMapping("/me")
    @Operation(summary = "Обновление своего профиля")
    public ResponseEntity<UserProfileDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userService.updateUser(userId, request);
        return ResponseEntity.ok(UserProfileDto.fromEntity(user));
    }

    @PostMapping("/me/avatar")
    @Operation(summary = "Загрузка аватара")
    public ResponseEntity<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        String avatarUrl = fileUploadService.uploadAvatar(userId, file);
        userService.updateAvatar(userId, avatarUrl);
        return ResponseEntity.ok(avatarUrl);
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Удаление аватара")
    public ResponseEntity<Void> deleteAvatar(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.deleteAvatar(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userUuid}")
    @Operation(summary = "Получение профиля пользователя по UUID")
    public ResponseEntity<UserDto> getUserByUuid(@PathVariable UUID userUuid) {
        User user = userService.getUserByUuid(userUuid);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск пользователей")
    public ResponseEntity<List<UserDto>> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> users = userService.searchUsers(query, PageRequest.of(page, size));
        List<UserDto> userDtos = users.getContent().stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDtos);
    }

    @GetMapping("/by-username/{username}")
    @Operation(summary = "Получение пользователя по имени")
    public ResponseEntity<UserDto> getUserByUsername(@PathVariable String username) {
        User user = userService.findByUsername(username);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @GetMapping("/by-email/{email}")
    @Operation(summary = "Получение пользователя по email")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        User user = userService.findByEmail(email);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @GetMapping("/{userUuid}/status")
    @Operation(summary = "Получение статуса пользователя (онлайн/оффлайн)")
    public ResponseEntity<UserStatusResponse> getUserStatus(@PathVariable UUID userUuid) {
        boolean isOnline = userService.isUserOnline(userUuid);
        return ResponseEntity.ok(new UserStatusResponse(isOnline));
    }

    @GetMapping("/me/sessions")
    @Operation(summary = "Получение списка активных сессий")
    public ResponseEntity<List<UserSessionDto>> getMySessions(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<UserSessionDto> sessions = userSessionService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/me/contacts")
    @Operation(summary = "Получение списка контактов")
    public ResponseEntity<List<UserDto>> getMyContacts(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<User> contacts = userService.getUserContacts(userId);
        return ResponseEntity.ok(contacts.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList()));
    }

    @PostMapping("/me/contacts/{contactUserUuid}")
    @Operation(summary = "Добавление контакта")
    public ResponseEntity<Void> addContact(
            @PathVariable UUID contactUserUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.addContact(userId, contactUserUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/contacts/{contactUserUuid}")
    @Operation(summary = "Удаление контакта")
    public ResponseEntity<Void> removeContact(
            @PathVariable UUID contactUserUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.removeContact(userId, contactUserUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/blocked")
    @Operation(summary = "Получение списка заблокированных пользователей")
    public ResponseEntity<List<UserDto>> getBlockedUsers(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<User> blockedUsers = userService.getBlockedUsers(userId);
        return ResponseEntity.ok(blockedUsers.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList()));
    }

    @PostMapping("/me/block/{userUuid}")
    @Operation(summary = "Блокировка пользователя")
    public ResponseEntity<Void> blockUser(
            @PathVariable UUID userUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.blockUser(userId, userUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/block/{userUuid}")
    @Operation(summary = "Разблокировка пользователя")
    public ResponseEntity<Void> unblockUser(
            @PathVariable UUID userUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.unblockUser(userId, userUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    @Operation(summary = "Удаление своего аккаунта")
    public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.deleteUser(userId);
        return ResponseEntity.ok().build();
    }

    record UserStatusResponse(boolean online) {}
}