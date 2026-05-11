package com.chat.server.controller;

import com.chat.server.config.JwtUtil;
import com.chat.server.dto.request.LoginRequestDto;
import com.chat.server.dto.request.RefreshTokenRequestDto;
import com.chat.server.dto.request.RegisterRequestDto;
import com.chat.server.dto.response.AuthResponseDto;
import com.chat.server.dto.response.UserDto;
import com.chat.server.entity.User;
import com.chat.server.entity.UserSession;
import com.chat.server.service.AuthService;
import com.chat.server.service.UserService;
import com.chat.server.service.UserSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API для аутентификации и регистрации")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final UserSessionService userSessionService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        log.info("Registering new user with username: {}", request.getUsername());

        User user = userService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

        // Сохраняем сессию
        userSessionService.createSession(
                user.getUserId(),
                token,
                refreshToken,
                request.getDeviceId(),
                request.getDeviceName(),
                request.getDeviceType(),
                request.getIpAddress(),
                request.getUserAgent()
        );

        return ResponseEntity.ok(AuthResponseDto.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .build());
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему")
    public ResponseEntity<AuthResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest) {
        log.info("User login attempt: {}", request.getUsername());

        User user = authService.authenticate(request.getUsername(), request.getPassword());

        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

        // Сохраняем сессию
        userSessionService.createSession(
                user.getUserId(),
                token,
                refreshToken,
                request.getDeviceId(),
                request.getDeviceName(),
                request.getDeviceType(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        // Обновляем статус онлайн
        userService.updateOnlineStatus(user.getUserId(), true);

        return ResponseEntity.ok(AuthResponseDto.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .build());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление JWT токена")
    public ResponseEntity<AuthResponseDto> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        log.info("Refreshing token");

        UserSession session = userSessionService.refreshSession(request.getRefreshToken());
        String newToken = jwtUtil.generateToken(session.getUserId(),
                userService.getUserById(session.getUserId()).getUsername());

        return ResponseEntity.ok(AuthResponseDto.builder()
                .token(newToken)
                .refreshToken(session.getRefreshToken())
                .userId(session.getUserId())
                .build());
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход из системы")
    public ResponseEntity<Void> logout(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        log.info("User logout: {}", userId);

        userSessionService.invalidateAllSessions(userId);
        userService.updateOnlineStatus(userId, false);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout/all-devices")
    @Operation(summary = "Выход на всех устройствах")
    public ResponseEntity<Void> logoutAllDevices(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        log.info("Logout from all devices for user: {}", userId);

        userSessionService.invalidateAllSessions(userId);
        userService.updateOnlineStatus(userId, false);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout/device")
    @Operation(summary = "Выход с конкретного устройства")
    public ResponseEntity<Void> logoutDevice(
            @RequestParam String deviceId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        log.info("Logout from device: {} for user: {}", deviceId, userId);

        userSessionService.invalidateSessionByDeviceId(userId, deviceId);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Получение информации о текущем пользователе")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Смена пароля")
    public ResponseEntity<Void> changePassword(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        authService.changePassword(
                userId,
                request.get("oldPassword"),
                request.get("newPassword")
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Запрос на восстановление пароля")
    public ResponseEntity<Void> forgotPassword(@RequestParam String email) {
        log.info("Password reset requested for email: {}", email);
        authService.sendPasswordResetToken(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Сброс пароля по токену")
    public ResponseEntity<Void> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword) {
        log.info("Resetting password with token");
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Подтверждение email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        log.info("Verifying email with token");
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }
}