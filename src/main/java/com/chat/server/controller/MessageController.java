package com.chat.server.controller;

import com.chat.server.dto.request.EditMessageRequestDto;
import com.chat.server.dto.request.ForwardMessageRequestDto;
import com.chat.server.dto.request.SendMessageRequestDto;
import com.chat.server.dto.response.MessageDto;
import com.chat.server.dto.response.MessageStatusDto;
import com.chat.server.entity.Message;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageService;
import com.chat.server.service.MessageStatusService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "API для управления сообщениями")
public class MessageController {

    private final MessageService messageService;
    private final MessageStatusService messageStatusService;
    private final ChatService chatService;

    @PostMapping("/{chatUuid}")
    @Operation(summary = "Отправка сообщения")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable UUID chatUuid,
            @Valid @RequestBody SendMessageRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        Message message = messageService.sendMessage(
                chatId,
                userId,
                request.getText(),
                request.getMessageType(),
                request.getReplyToMessageUuid(),
                request.getAttachments()
        );

        return ResponseEntity.ok(MessageDto.fromEntity(message));
    }

    @GetMapping("/{chatUuid}")
    @Operation(summary = "Получение сообщений чата с пагинацией")
    public ResponseEntity<Page<MessageDto>> getMessages(
            @PathVariable UUID chatUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        Page<Message> messages = messageService.getChatMessages(chatId, userId, PageRequest.of(page, size));
        Page<MessageDto> messageDtos = messages.map(MessageDto::fromEntity);

        return ResponseEntity.ok(messageDtos);
    }

    @GetMapping("/{chatUuid}/before/{messageUuid}")
    @Operation(summary = "Получение сообщений до указанного")
    public ResponseEntity<List<MessageDto>> getMessagesBefore(
            @PathVariable UUID chatUuid,
            @PathVariable UUID messageUuid,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        List<Message> messages = messageService.getMessagesBeforeMessage(chatId, userId, messageUuid, limit);
        List<MessageDto> messageDtos = messages.stream()
                .map(MessageDto::fromEntity)
                .toList();

        return ResponseEntity.ok(messageDtos);
    }

    @GetMapping("/{chatUuid}/after/{timestamp}")
    @Operation(summary = "Получение новых сообщений после указанного времени")
    public ResponseEntity<List<MessageDto>> getNewMessages(
            @PathVariable UUID chatUuid,
            @PathVariable String timestamp,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        LocalDateTime since = LocalDateTime.parse(timestamp);
        List<Message> messages = messageService.getMessagesAfter(chatId, userId, since);
        List<MessageDto> messageDtos = messages.stream()
                .map(MessageDto::fromEntity)
                .toList();

        return ResponseEntity.ok(messageDtos);
    }

    @GetMapping("/{messageUuid}")
    @Operation(summary = "Получение сообщения по UUID")
    public ResponseEntity<MessageDto> getMessageByUuid(
            @PathVariable UUID messageUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        Message message = messageService.getMessageByUuid(messageUuid);
        chatService.validateUserAccessToChat(message.getChatId(), userId);

        return ResponseEntity.ok(MessageDto.fromEntity(message));
    }

    @PutMapping("/{messageUuid}")
    @Operation(summary = "Редактирование сообщения")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable UUID messageUuid,
            @Valid @RequestBody EditMessageRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        Message message = messageService.editMessage(messageUuid, userId, request.getText());
        return ResponseEntity.ok(MessageDto.fromEntity(message));
    }

    @DeleteMapping("/{messageUuid}")
    @Operation(summary = "Удаление сообщения (soft delete)")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID messageUuid,
            @RequestParam(defaultValue = "false") boolean hardDelete,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        messageService.deleteMessage(messageUuid, userId, hardDelete);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forward")
    @Operation(summary = "Пересылка сообщения")
    public ResponseEntity<MessageDto> forwardMessage(
            @Valid @RequestBody ForwardMessageRequestDto request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long targetChatId = chatService.getChatIdByUuid(request.getTargetChatUuid());

        Message message = messageService.forwardMessage(request.getMessageUuid(), targetChatId, userId);
        return ResponseEntity.ok(MessageDto.fromEntity(message));
    }

    @PostMapping("/{chatUuid}/read")
    @Operation(summary = "Отметить сообщения как прочитанные")
    public ResponseEntity<Void> markMessagesAsRead(
            @PathVariable UUID chatUuid,
            @RequestParam UUID upToMessageUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        messageStatusService.markMessagesAsRead(chatId, userId, upToMessageUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{chatUuid}/status/{messageUuid}")
    @Operation(summary = "Получение статусов доставки сообщения")
    public ResponseEntity<List<MessageStatusDto>> getMessageStatuses(
            @PathVariable UUID chatUuid,
            @PathVariable UUID messageUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        List<MessageStatusDto> statuses = messageStatusService.getMessageStatuses(messageUuid);
        return ResponseEntity.ok(statuses);
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск сообщений по всем чатам")
    public ResponseEntity<List<MessageDto>> searchMessages(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        Page<Message> messages = messageService.searchMessages(userId, query, PageRequest.of(page, size));
        List<MessageDto> messageDtos = messages.stream()
                .map(MessageDto::fromEntity)
                .toList();

        return ResponseEntity.ok(messageDtos);
    }

    @GetMapping("/{chatUuid}/search")
    @Operation(summary = "Поиск сообщений в конкретном чате")
    public ResponseEntity<List<MessageDto>> searchMessagesInChat(
            @PathVariable UUID chatUuid,
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        List<Message> messages = messageService.searchMessagesInChat(chatId, userId, query, limit);
        List<MessageDto> messageDtos = messages.stream()
                .map(MessageDto::fromEntity)
                .toList();

        return ResponseEntity.ok(messageDtos);
    }

    @GetMapping("/{chatUuid}/pin/{messageUuid}")
    @Operation(summary = "Закрепить сообщение в чате")
    public ResponseEntity<Void> pinMessage(
            @PathVariable UUID chatUuid,
            @PathVariable UUID messageUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        messageService.pinMessage(chatId, messageUuid, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{chatUuid}/pin/{messageUuid}")
    @Operation(summary = "Открепить сообщение")
    public ResponseEntity<Void> unpinMessage(
            @PathVariable UUID chatUuid,
            @PathVariable UUID messageUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        messageService.unpinMessage(chatId, messageUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{chatUuid}/pinned")
    @Operation(summary = "Получение закрепленных сообщений")
    public ResponseEntity<List<MessageDto>> getPinnedMessages(
            @PathVariable UUID chatUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        List<Message> messages = messageService.getPinnedMessages(chatId);
        List<MessageDto> messageDtos = messages.stream()
                .map(MessageDto::fromEntity)
                .toList();

        return ResponseEntity.ok(messageDtos);
    }
}