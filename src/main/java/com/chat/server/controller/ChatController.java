package com.chat.server.controller;

import com.chat.server.dto.request.CreateGroupChatRequestDto;
import com.chat.server.dto.request.CreatePrivateChatRequestDto;
import com.chat.server.dto.request.UpdateChatRequestDto;
import com.chat.server.dto.response.ChatDetailsResponseDto;
import com.chat.server.dto.response.ChatResponseDto;
import com.chat.server.dto.response.ParticipantInfoDto;
import com.chat.server.service.ChatService;
import com.chat.server.service.FileUploadService;
import com.chat.server.service.ParticipantService;
import com.chat.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Tag(name = "Chats", description = "API для управления чатами")
public class ChatController {

    private final ChatService chatService;
    private final ParticipantService participantService;
    private final FileUploadService fileUploadService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Получение всех чатов пользователя")
    public ResponseEntity<List<ChatResponseDto>> getAllByUser(Authentication authentication) {
        var userUUID = UUID.fromString(authentication.getName());
        var userId = userService.getUserIdByUuid(userUUID);
        List<ChatResponseDto> chats = chatService.getUserChatsWithDetails(userId);
        return ResponseEntity.ok(chats);
    }

    @GetMapping("/{chatUuid}")
    @Operation(summary = "Получение информации о чате")
    public ResponseEntity<ChatDetailsResponseDto> getByChatUUID(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        UUID userUUID = UUID.fromString(authentication.getName());
        Long userId = userService.getUserIdByUuid(userUUID);
        Long chatId = chatService.getChatIdByUuid(chatUuid);
        chatService.validateUserAccessToChat(chatId, userId);

        var chat = chatService.getChatDetails(chatId, userId);
        return ResponseEntity.ok(chat);
    }

    @PostMapping("/private")
    @Operation(summary = "Создание приватного чата")
    public ResponseEntity<ChatResponseDto> createPrivateChat(
            @Valid @RequestBody CreatePrivateChatRequestDto request,
            Authentication authentication) {
        var userUUID = UUID.fromString(authentication.getName());
        var userId = userService.getUserIdByUuid(userUUID);
        var otherUserId = userService.getUserIdByUuid(request.getOtherUserUuid());

        return ResponseEntity.ok(chatService.createPrivateChat(userId, otherUserId));
    }

    @PostMapping("/group")
    @Operation(summary = "Создание группового чата")
    public ResponseEntity<ChatResponseDto> createGroupChat(
            @Valid @RequestBody CreateGroupChatRequestDto request,
            Authentication authentication) {
        UUID userUUID = UUID.fromString(authentication.getName());
        Long userId = userService.getUserIdByUuid(userUUID);

        List<Long> memberIds = request.getMemberUuids().stream()
                .map(userService::getUserIdByUuid)
                .toList();

        return ResponseEntity.ok(chatService.createGroupChat(request.getTitle(), userId, memberIds));
    }

    @PutMapping("/{chatUuid}")
    @Operation(summary = "Обновление информации о чате")
    public ResponseEntity<ChatResponseDto> updateChat(
            @PathVariable UUID chatUuid,
            @Valid @RequestBody UpdateChatRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        return ResponseEntity.ok(chatService.updateChat(chatId, request));
    }

    @PostMapping("/{chatUuid}/avatar")
    @Operation(summary = "Загрузка аватара чата")
    public ResponseEntity<String> uploadChatAvatar(
            @PathVariable UUID chatUuid,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        var avatarUrl = fileUploadService.uploadChatAvatar(chatId, file);
        chatService.updateAvatar(chatId, avatarUrl);

        return ResponseEntity.ok(avatarUrl);
    }

    @DeleteMapping("/{chatUuid}")
    @Operation(summary = "Удаление чата")
    public ResponseEntity<Void> deleteChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.deleteChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chatUuid}/archive")
    @Operation(summary = "Архивировать чат")
    public ResponseEntity<Void> archiveChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.archiveChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chatUuid}/unarchive")
    @Operation(summary = "Разархивировать чат")
    public ResponseEntity<Void> unarchiveChat(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.unarchiveChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{chatUuid}/participants")
    @Operation(summary = "Получение списка участников чата")
    public ResponseEntity<List<ParticipantInfoDto>> getChatParticipants(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        var participants = participantService.getChatParticipantsWithDetails(chatId);
        return ResponseEntity.ok(participants);
    }

    @GetMapping("/{chatUuid}/unread-count")
    @Operation(summary = "Получение количества непрочитанных сообщений")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = userService.getUserIdByUuid(UUID.fromString(authentication.getName()));
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        long unreadCount = chatService.getUnreadMessagesCount(chatId, userId);
        return ResponseEntity.ok(new UnreadCountResponse(unreadCount));
    }

    @GetMapping("/unread/all")
    @Operation(summary = "Получение общего количества непрочитанных сообщений")
    public ResponseEntity<UnreadCountResponse> getTotalUnreadCount(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        long totalUnread = chatService.getTotalUnreadCount(userId);
        return ResponseEntity.ok(new UnreadCountResponse(totalUnread));
    }

    record UnreadCountResponse(long count) {}
}