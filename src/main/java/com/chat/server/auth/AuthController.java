package com.chat.server.auth;

import com.chat.server.user.UserDto;
import com.chat.server.user.User;
import com.chat.server.exception.UnauthorizedException;
import com.chat.server.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

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

        String token = jwtUtil.generateToken(user.getUserUuid(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserUuid());

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

        return getResponse(token, refreshToken, user);
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему")
    public ResponseEntity<AuthResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest) {
        log.info("User login attempt: {}", request.getUsername());

        User user = authService.authenticate(request.getUsername(), request.getPassword());

        String token = jwtUtil.generateToken(user.getUserUuid(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserUuid());

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

        return getResponse(token, refreshToken, user);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление JWT токена")
    public ResponseEntity<AuthResponseDto> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        log.info("Refreshing token");

        if (!jwtUtil.isRefreshToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Provided token is not a refresh token");
        }

        UserSession session = userSessionService.refreshSession(request.getRefreshToken());
        UUID userIdFromToken = jwtUtil.getUserIdFromToken(request.getRefreshToken());
        User user = userService.getUserById(session.getUserId());
        String newToken = jwtUtil.generateToken(userIdFromToken, user.getUsername());
        String newRefreshToken = jwtUtil.generateRefreshToken(userIdFromToken);
        userSessionService.rotateRefreshToken(session.getSessionId(), newRefreshToken);

        return getResponse(newToken, newRefreshToken, user);
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход из системы")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(authentication.getName());
        log.info("User logout: {}", userId);

        String token = extractBearerToken(httpRequest);
        if (token != null) {
            userSessionService.invalidateSession(token);
        } else {
            userSessionService.invalidateAllSessions(userId);
        }
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

    private static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private static ResponseEntity<AuthResponseDto> getResponse(String token, String refreshToken, User user) {
        return ResponseEntity.ok(AuthResponseDto.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userUuid(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build());
    }
}