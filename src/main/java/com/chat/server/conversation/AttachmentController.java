package com.chat.server.conversation;

import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam UUID chatUuid,
            @RequestParam(value = "width", required = false) Integer width,
            @RequestParam(value = "height", required = false) Integer height,
            @RequestParam(value = "duration", required = false) Integer duration,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Long chatId = chatService.getChatIdByUuid(chatUuid);

        chatService.validateUserAccessToChat(chatId, userId);

        Attachment attachment = attachmentService.uploadAttachment(
                file, thumbnail, chatId, userId, width, height, duration);
        return ResponseEntity.ok(AttachmentDto.fromEntity(attachment));
    }

    @GetMapping("/{attachmentUuid}")
    @Operation(summary = "Скачивание вложения")
    public ResponseEntity<?> downloadAttachment(
            @PathVariable UUID attachmentUuid,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            Authentication authentication) throws IOException {
        Long userId = Long.parseLong(authentication.getName());

        Attachment attachment = attachmentService.getAttachmentByUuid(attachmentUuid);

        // Проверяем доступ к чату
        chatService.validateUserAccessToChat(attachment.getChatId(), userId);

        MediaType contentType = mediaType(attachment.getMimeType());
        String disposition = "inline; filename=\"" +
                com.chat.server.util.FileNames.sanitize(attachment.getFileName()) + "\"";
        Long size = attachment.getFileSize();

        // Range нужен видеоплееру и аудио: без него перемотка работает только
        // после полной загрузки. Читаем только запрошенный кусок из MinIO,
        // не пытаясь повторно использовать InputStreamResource.
        if (rangeHeader != null && !rangeHeader.isBlank() && size != null && size > 0) {
            List<HttpRange> ranges;
            try {
                ranges = HttpRange.parseRanges(rangeHeader);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
            }
            if (ranges.size() != 1) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
            }
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(size);
            long end = range.getRangeEnd(size);
            long length = end - start + 1;
            Resource resource = attachmentService.downloadAttachmentRange(attachment, start, length);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size)
                    .contentLength(length)
                    .contentType(contentType)
                    .body(resource);
        }

        Resource resource = attachmentService.downloadAttachment(attachment);
        var builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(contentType);
        if (size != null && size > 0) {
            builder.contentLength(size);
        }
        return builder.body(resource);
    }

    @GetMapping("/{attachmentUuid}/preview")
    @Operation(summary = "Просмотр превью вложения")
    public ResponseEntity<Resource> previewAttachment(
            @PathVariable UUID attachmentUuid,
            Authentication authentication) throws IOException {
        Long userId = Long.parseLong(authentication.getName());

        Attachment attachment = attachmentService.getAttachmentByUuid(attachmentUuid);

        // Проверяем доступ к чату
        chatService.validateUserAccessToChat(attachment.getChatId(), userId);

        Resource resource = attachmentService.getPreview(attachment);

        return ResponseEntity.ok()
                .contentType(mediaType(attachment.getMimeType()))
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

    private static MediaType mediaType(String mimeType) {
        return mimeType != null ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM;
    }
}