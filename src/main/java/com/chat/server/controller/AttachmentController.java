package com.chat.server.controller;

import com.chat.server.dto.response.AttachmentDto;
import com.chat.server.entity.Attachment;
import com.chat.server.service.AttachmentService;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments", description = "API для управления вложениями")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final MessageService messageService;

    @PostMapping("/upload")
    @Operation(summary = "Загрузка вложения для сообщения")
    public ResponseEntity<AttachmentDto> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam UUID chatUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        Attachment attachment = attachmentService.uploadAttachment(file, chatId, userId);
        return ResponseEntity.ok(AttachmentDto.fromEntity(attachment));
    }

    @GetMapping("/{attachmentUuid}")
    @Operation(summary = "Скачивание вложения")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable UUID attachmentUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        Attachment attachment = attachmentService.getAttachmentByUuid(attachmentUuid);

        // Проверяем доступ к чату
        chatService.validateUserAccessToChat(attachment.getChatId(), userId);

        Resource resource = attachmentService.downloadAttachment(attachment);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .body(resource);
    }

    @GetMapping("/{attachmentUuid}/preview")
    @Operation(summary = "Просмотр превью вложения")
    public ResponseEntity<Resource> previewAttachment(
            @PathVariable UUID attachmentUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        Attachment attachment = attachmentService.getAttachmentByUuid(attachmentUuid);

        // Проверяем доступ к чату
        chatService.validateUserAccessToChat(attachment.getChatId(), userId);

        Resource resource = attachmentService.getPreview(attachment);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .body(resource);
    }

    @DeleteMapping("/{attachmentUuid}")
    @Operation(summary = "Удаление вложения")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID attachmentUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        attachmentService.deleteAttachment(attachmentUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/message/{messageUuid}")
    @Operation(summary = "Получение всех вложений сообщения")
    public ResponseEntity<List<AttachmentDto>> getMessageAttachments(
            @PathVariable UUID messageUuid,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        var message = messageService.getMessageByUuid(messageUuid);
        chatService.validateUserAccessToChat(message.getChatId(), userId);

        List<Attachment> attachments = attachmentService.getAttachmentsByMessageId(message.getMessageId());
        List<AttachmentDto> attachmentDtos = attachments.stream()
                .map(AttachmentDto::fromEntity)
                .toList();

        return ResponseEntity.ok(attachmentDtos);
    }

    @GetMapping("/chat/{chatUuid}")
    @Operation(summary = "Получение всех вложений чата с пагинацией")
    public ResponseEntity<List<AttachmentDto>> getChatAttachments(
            @PathVariable UUID chatUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        var attachments = attachmentService.getAttachmentsByChatId(chatId, page, size);
        List<AttachmentDto> attachmentDtos = attachments.stream()
                .map(AttachmentDto::fromEntity)
                .toList();

        return ResponseEntity.ok(attachmentDtos);
    }

    @GetMapping("/chat/{chatUuid}/media")
    @Operation(summary = "Получение медиа-файлов чата (фото/видео)")
    public ResponseEntity<List<AttachmentDto>> getChatMedia(
            @PathVariable UUID chatUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        var attachments = attachmentService.getMediaAttachmentsByChatId(chatId, page, size);
        List<AttachmentDto> attachmentDtos = attachments.stream()
                .map(AttachmentDto::fromEntity)
                .toList();

        return ResponseEntity.ok(attachmentDtos);
    }
}