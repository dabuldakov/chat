package com.chat.server.controller;

import com.chat.server.dto.request.AddParticipantsRequestDto;
import com.chat.server.dto.request.UpdateParticipantRoleRequestDto;
import com.chat.server.dto.response.ParticipantInfoDto;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageService;
import com.chat.server.service.ParticipantService;
import com.chat.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chats/{chatUuid}/participants")
@RequiredArgsConstructor
@Tag(name = "Participants", description = "API для управления участниками чатов")
public class ParticipantController {

    private final ChatService chatService;
    private final ParticipantService participantService;
    private final UserService userService;
    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "Получение списка участников чата")
    public ResponseEntity<List<ParticipantInfoDto>> getParticipants(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        List<ParticipantInfoDto> participants = participantService.getChatParticipantsWithDetails(chatId);
        return ResponseEntity.ok(participants);
    }

    @PostMapping
    @Operation(summary = "Добавление участников в групповой чат")
    public ResponseEntity<Void> addParticipants(
            @PathVariable UUID chatUuid,
            @Valid @RequestBody AddParticipantsRequestDto request,
            Authentication authentication) {
        Long requesterId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        // Конвертируем UUID участников в Long ID
        List<Long> memberIds = request.getMemberUuids().stream()
                .map(userService::getUserIdByUuid)
                .toList();

        participantService.addParticipantsToGroup(chatId, memberIds, requesterId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userUuid}")
    @Operation(summary = "Удаление участника из группового чата")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable UUID chatUuid,
            @PathVariable UUID userUuid,
            Authentication authentication) {
        Long requesterId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);
        Long userIdToRemove = userService.getUserIdByUuid(userUuid);

        participantService.removeParticipantFromGroup(chatId, userIdToRemove, requesterId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/leave")
    @Operation(summary = "Выход из группового чата")
    public ResponseEntity<Void> leaveChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        participantService.leaveGroup(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userUuid}/role")
    @Operation(summary = "Изменение роли участника (только для создателя)")
    public ResponseEntity<Void> updateParticipantRole(
            @PathVariable UUID chatUuid,
            @PathVariable UUID userUuid,
            @Valid @RequestBody UpdateParticipantRoleRequestDto request,
            Authentication authentication) {
        Long requesterId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);
        Long targetUserId = userService.getUserIdByUuid(userUuid);

        participantService.updateParticipantRole(chatId, targetUserId, requesterId, request.getRole());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mute")
    @Operation(summary = "Отключить уведомления в чате")
    public ResponseEntity<Void> muteChat(
            @PathVariable UUID chatUuid,
            @RequestParam(required = false) Integer durationHours,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        participantService.muteChat(chatId, userId, durationHours);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/mute")
    @Operation(summary = "Включить уведомления в чате")
    public ResponseEntity<Void> unmuteChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        participantService.unmuteChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pin")
    @Operation(summary = "Закрепить чат в списке")
    public ResponseEntity<Void> pinChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        participantService.pinChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/pin")
    @Operation(summary = "Открепить чат из списка")
    public ResponseEntity<Void> unpinChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        participantService.unpinChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Получение информации о своем участии в чате")
    public ResponseEntity<MyParticipationDto> getMyParticipation(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        var participant = participantService.getParticipant(chatId, userId);

        return ResponseEntity.ok(MyParticipationDto.builder()
                .joinedAt(participant.getJoinedAt())
                .role(participant.getRole().name())
                .lastReadMessageUuid(participant.getLastReadMessageId() != null ?
                        messageService.getMessageById(participant.getLastReadMessageId()).getMessageUuid() : null)
                .isMuted(participant.isMuted())
                .mutedUntil(participant.getMutedUntil())
                .isPinned(participant.getIsPinned())
                .build());
    }

    record MyParticipationDto(
            LocalDateTime joinedAt,
            String role,
            UUID lastReadMessageUuid,
            boolean isMuted,
            LocalDateTime mutedUntil,
            boolean isPinned
    ) {
        @Builder
        public MyParticipationDto {}
    }
}