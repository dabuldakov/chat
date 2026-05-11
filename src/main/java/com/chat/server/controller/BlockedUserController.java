package com.chat.server.controller;

import com.chat.server.dto.request.BlockUserRequestDto;
import com.chat.server.dto.response.BlockedUserDto;
import com.chat.server.entity.BlockedUser;
import com.chat.server.entity.User;
import com.chat.server.service.BlockedUserService;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/blocked")
@RequiredArgsConstructor
@Tag(name = "Blocked Users", description = "API для управления заблокированными пользователями")
public class BlockedUserController {

    private final BlockedUserService blockedUserService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Получение списка заблокированных пользователей")
    public ResponseEntity<List<BlockedUserDto>> getBlockedUsers(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<BlockedUserDto> blockedUsers = blockedUserService.getBlockedUsers(userId);
        return ResponseEntity.ok(blockedUsers);
    }

    @GetMapping("/page")
    @Operation(summary = "Получение списка заблокированных с пагинацией")
    public ResponseEntity<Page<BlockedUserDto>> getBlockedUsersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Page<BlockedUserDto> blockedUsers = blockedUserService.getBlockedUsersPaginated(
                userId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(blockedUsers);
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск среди заблокированных")
    public ResponseEntity<Page<BlockedUserDto>> searchBlockedUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Page<BlockedUserDto> blockedUsers = blockedUserService.searchBlockedUsers(
                userId, query, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(blockedUsers);
    }

    @GetMapping("/{blockUuid}")
    @Operation(summary = "Получение информации о блокировке")
    public ResponseEntity<BlockedUserDto> getBlockByUuid(
            @PathVariable UUID blockUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        BlockedUserDto block = blockedUserService.getBlockByUuid(userId, blockUuid);
        return ResponseEntity.ok(block);
    }

    @PostMapping
    @Operation(summary = "Блокировка пользователя")
    public ResponseEntity<BlockedUserDto> blockUser(
            @Valid @RequestBody BlockUserRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        BlockedUser block = blockedUserService.blockUser(userId, request);

        User blockedUser = userService.getUserById(block.getBlockedUserId());
        return ResponseEntity.ok(BlockedUserDto.fromEntity(block, blockedUser));
    }

    @DeleteMapping("/{blockUuid}")
    @Operation(summary = "Разблокировка пользователя")
    public ResponseEntity<Void> unblockUser(
            @PathVariable UUID blockUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        blockedUserService.unblockUser(userId, blockUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @Operation(summary = "Разблокировка всех пользователей")
    public ResponseEntity<Void> unblockAllUsers(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        blockedUserService.unblockAllUsers(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check/{userUuid}")
    @Operation(summary = "Проверка, заблокирован ли пользователь")
    public ResponseEntity<BlockStatusResponse> isBlocked(
            @PathVariable UUID userUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long targetUserId = userService.getUserIdByUuid(userUuid);

        boolean isBlocked = blockedUserService.isBlocked(userId, targetUserId);
        boolean isBlockedBy = blockedUserService.isBlocked(targetUserId, userId);
        boolean isMutual = blockedUserService.isMutualBlock(userId, targetUserId);

        return ResponseEntity.ok(new BlockStatusResponse(isBlocked, isBlockedBy, isMutual));
    }

    record BlockStatusResponse(boolean blocked, boolean blockedBy, boolean mutual) {}
}