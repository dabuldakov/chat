package com.chat.server.controller;

import com.chat.server.dto.response.SyncResponseDto;
import com.chat.server.service.SyncService;
import com.chat.server.service.SyncStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "API для синхронизации данных с клиентом")
public class SyncController {

    private final SyncService syncService;

    @GetMapping
    @Operation(summary = "Полная синхронизация данных пользователя")
    public ResponseEntity<SyncResponseDto> sync(
            @RequestParam(required = false) String lastSyncTime,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        LocalDateTime since = lastSyncTime != null
                ? LocalDateTime.parse(lastSyncTime)
                : LocalDateTime.now().minusDays(7);

        SyncResponseDto syncData = syncService.syncUserData(userId, since);
        return ResponseEntity.ok(syncData);
    }

    @GetMapping("/messages")
    @Operation(summary = "Синхронизация только сообщений")
    public ResponseEntity<SyncResponseDto> syncMessages(
            @RequestParam String lastSyncTime,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        LocalDateTime since = LocalDateTime.parse(lastSyncTime);
        SyncResponseDto syncData = syncService.syncMessagesOnly(userId, since);

        return ResponseEntity.ok(syncData);
    }

    @GetMapping("/chats")
    @Operation(summary = "Синхронизация только чатов")
    public ResponseEntity<SyncResponseDto> syncChats(
            @RequestParam(required = false) String lastSyncTime,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        LocalDateTime since = lastSyncTime != null
                ? LocalDateTime.parse(lastSyncTime)
                : LocalDateTime.now().minusDays(30);

        SyncResponseDto syncData = syncService.syncChatsOnly(userId, since);
        return ResponseEntity.ok(syncData);
    }

    @GetMapping("/status")
    @Operation(summary = "Получение статуса синхронизации")
    public ResponseEntity<SyncStatusResponse> getSyncStatus(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        var status = syncService.getSyncStatus(userId);
        return ResponseEntity.ok(status);
    }
}