package com.chat.server.conversation;

import com.chat.server.conversation.Message;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.MessageService;
import com.chat.server.storage.FileStorage;
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final MessageService messageService;
    private final ChatService chatService;
    private final FileStorage fileUploadService;

    /**
     * Без @Transactional: на время загрузки файла в MinIO не удерживается
     * соединение из пула (долгий сетевой ввод-вывод вне транзакции).
     */
    public Attachment uploadAttachment(
            MultipartFile file,
            MultipartFile thumbnail,
            Long chatId,
            Long userId,
            Integer width,
            Integer height,
            Integer duration
    ) {
        log.info("Uploading attachment for chat: {} by user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        AttachmentPolicy.Rule rule = AttachmentPolicy.validate(file);

        String storedName = fileUploadService.store(file, "attachments",
                UUID.randomUUID().toString() + "_" + file.getOriginalFilename());

        String thumbnailKey = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            AttachmentPolicy.validateThumbnail(thumbnail);
            thumbnailKey = fileUploadService.store(thumbnail, "attachment-thumbnails",
                    UUID.randomUUID().toString() + ".jpg");
        }

        Attachment attachment = Attachment.builder()
                .attachmentUuid(UUID.randomUUID())
                .chatId(chatId)
                .uploaderId(userId)
                .fileName(com.chat.server.util.FileNames.sanitize(file.getOriginalFilename()))
                .fileUrl(storedName)
                .thumbnailUrl(thumbnailKey)
                .fileSize(file.getSize())
                .mimeType(rule.mimeType())
                .type(rule.type())
                .width(width)
                .height(height)
                .duration(duration)
                .build();

        return attachmentRepository.save(attachment);
    }

    @Transactional
    public Attachment attachToMessage(UUID attachmentUuid, UUID messageUuid) {
        Attachment attachment = getAttachmentByUuid(attachmentUuid);
        Message message = messageService.getMessageByUuid(messageUuid);

        if (!attachment.getChatId().equals(message.getChatId())) {
            throw new IllegalArgumentException("Attachment and message must be in same chat");
        }

        attachment.setMessageId(message.getMessageId());

        // Обновляем флаг has_attachments у сообщения
        message.setHasAttachments(true);
        messageService.updateMessageAttachmentsFlag(message.getMessageId(), true);

        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public Attachment getAttachmentByUuid(UUID attachmentUuid) {
        return attachmentRepository.findByAttachmentUuid(attachmentUuid)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
    }

    @Transactional(readOnly = true)
    public List<Attachment> getAttachmentsByMessageId(Long messageId) {
        return attachmentRepository.findByMessageId(messageId);
    }

    @Transactional(readOnly = true)
    public List<Attachment> getAttachmentsByChatId(Long chatId, int page, int size) {
        int offset = page * size;
        return attachmentRepository.findByChatId(chatId, size, offset);
    }

    @Transactional(readOnly = true)
    public List<Attachment> getMediaAttachmentsByChatId(Long chatId, int page, int size) {
        int offset = page * size;
        return attachmentRepository.findMediaByChatId(chatId, size, offset);
    }

    @Transactional(readOnly = true)
    public Resource downloadAttachment(Attachment attachment) throws IOException {
        return fileUploadService.load(attachment.getFileUrl());
    }

    /** Часть файла для Range-запроса (перемотка видео/аудио). */
    @Transactional(readOnly = true)
    public Resource downloadAttachmentRange(Attachment attachment, long offset, long length) {
        return fileUploadService.load(attachment.getFileUrl(), offset, length);
    }

    @Transactional(readOnly = true)
    public Resource getPreview(Attachment attachment) throws IOException {
        if (attachment.isImage()) {
            return downloadAttachment(attachment);
        }

        // Для видео/документов возвращаем превью если есть
        if (attachment.getThumbnailUrl() != null) {
            return fileUploadService.load(attachment.getThumbnailUrl());
        }

        throw new NotFoundException("Preview not available");
    }

    @Transactional
    public void deleteAttachment(UUID attachmentUuid, Long userId) {
        Attachment attachment = getAttachmentByUuid(attachmentUuid);

        chatService.validateUserAccessToChat(attachment.getChatId(), userId);

        // Только загрузивший или автор сообщения могут удалить
        if (!attachment.getUploaderId().equals(userId)) {
            if (attachment.getMessageId() != null) {
                Message message = messageService.getMessageById(attachment.getMessageId());
                if (!message.getSenderId().equals(userId)) {
                    throw new AccessDeniedException("Cannot delete this attachment");
                }
            } else {
                throw new AccessDeniedException("Cannot delete this attachment");
            }
        }

        Long messageId = attachment.getMessageId();

        // Удаляем файл
        fileUploadService.delete(attachment.getFileUrl());
        if (attachment.getThumbnailUrl() != null) {
            fileUploadService.delete(attachment.getThumbnailUrl());
        }

        attachmentRepository.delete(attachment);

        // Если у сообщения больше нет вложений, обновляем флаг
        if (messageId != null) {
            long remainingAttachments = attachmentRepository.countByMessageId(messageId);
            if (remainingAttachments == 0) {
                messageService.updateMessageAttachmentsFlag(messageId, false);
            }
        }

        log.info("Attachment deleted: {}", attachmentUuid);
    }

    @Transactional
    public void deleteAllAttachmentsByMessageId(Long messageId) {
        List<Attachment> attachments = attachmentRepository.findByMessageId(messageId);
        for (Attachment attachment : attachments) {
            fileUploadService.delete(attachment.getFileUrl());
            if (attachment.getThumbnailUrl() != null) {
                fileUploadService.delete(attachment.getThumbnailUrl());
            }
        }
        attachmentRepository.deleteByMessageId(messageId);
        log.info("All attachments deleted for message: {}", messageId);
    }

    @Transactional
    public void cleanupOrphanedAttachments() {
        LocalDateTime cutoffDate = LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
        int deletedCount = attachmentRepository.deleteOrphanedAttachments(cutoffDate);
        log.info("Cleaned up {} orphaned attachments", deletedCount);
    }

    @Transactional(readOnly = true)
    public long getAttachmentsCountByChatId(Long chatId) {
        return attachmentRepository.countByChatId(chatId);
    }

    @Transactional(readOnly = true)
    public long getMediaCountByChatId(Long chatId) {
        return attachmentRepository.countMediaByChatId(chatId);
    }


}