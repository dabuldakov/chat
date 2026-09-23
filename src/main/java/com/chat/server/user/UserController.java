package com.chat.server.user;

import com.chat.server.auth.UserSessionDto;
import com.chat.server.auth.UserSessionService;
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
    private final UserSessionService userSessionService;
    private final UserAvatarService avatars;

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
    public ResponseEntity<AvatarResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(new AvatarResponse(avatars.upload(userId, file)));
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Удаление аватара")
    public ResponseEntity<Void> deleteAvatar(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        avatars.delete(userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me/fcm-token")
    @Operation(summary = "Регистрация FCM-токена устройства для пуш-уведомлений")
    public ResponseEntity<Void> registerFcmToken(
            @RequestBody FcmTokenRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userSessionService.registerFcmToken(userId, request.deviceId(), request.token());
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

    @DeleteMapping("/me")
    @Operation(summary = "Удаление своего аккаунта")
    public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        userService.deleteUser(userId);
        return ResponseEntity.ok().build();
    }

    public record AvatarResponse(String avatarUrl) {}

    public record FcmTokenRequest(String token, String deviceId) {}

    record UserStatusResponse(boolean online) {}
}
