package com.chat.server.service;

import com.chat.server.entity.Attachment;
import com.chat.server.entity.Message;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final MessageService messageService;
    private final ChatService chatService;
    private final FileUploadService fileUploadService;

    private final Path storageLocation = Paths.get("/var/chat-app/uploads");

    @Transactional
    public Attachment uploadAttachment(MultipartFile file, Long chatId, Long userId) {
        log.info("Uploading attachment for chat: {} by user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        String fileName = fileUploadService.store(file, "attachments",
                UUID.randomUUID().toString() + "_" + file.getOriginalFilename());

        Attachment attachment = Attachment.builder()
                .attachmentUuid(UUID.randomUUID())
                .chatId(chatId)
                .uploaderId(userId)
                .fileName(file.getOriginalFilename())
                .fileUrl(fileName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .type(detectAttachmentType(file.getContentType()))
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
        Path filePath = storageLocation.resolve(attachment.getFileUrl());
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            throw new NotFoundException("File not found");
        }

        return resource;
    }

    @Transactional(readOnly = true)
    public Resource getPreview(Attachment attachment) throws IOException {
        if (attachment.isImage()) {
            return downloadAttachment(attachment);
        }

        // Для видео/документов возвращаем превью если есть
        if (attachment.getThumbnailUrl() != null) {
            Path thumbPath = storageLocation.resolve(attachment.getThumbnailUrl());
            return new UrlResource(thumbPath.toUri());
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
        try {
            Files.deleteIfExists(storageLocation.resolve(attachment.getFileUrl()));
            if (attachment.getThumbnailUrl() != null) {
                Files.deleteIfExists(storageLocation.resolve(attachment.getThumbnailUrl()));
            }
        } catch (IOException e) {
            log.warn("Failed to delete file for attachment: {}", attachmentUuid, e);
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
            try {
                Files.deleteIfExists(storageLocation.resolve(attachment.getFileUrl()));
                if (attachment.getThumbnailUrl() != null) {
                    Files.deleteIfExists(storageLocation.resolve(attachment.getThumbnailUrl()));
                }
            } catch (IOException e) {
                log.warn("Failed to delete file for attachment: {}", attachment.getAttachmentUuid(), e);
            }
        }
        attachmentRepository.deleteByMessageId(messageId);
        log.info("All attachments deleted for message: {}", messageId);
    }

    @Transactional
    public void cleanupOrphanedAttachments() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusHours(24);
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

    private Attachment.AttachmentType detectAttachmentType(String mimeType) {
        if (mimeType == null) return Attachment.AttachmentType.OTHER;

        if (mimeType.startsWith("image/")) return Attachment.AttachmentType.IMAGE;
        if (mimeType.startsWith("video/")) return Attachment.AttachmentType.VIDEO;
        if (mimeType.startsWith("audio/")) return Attachment.AttachmentType.AUDIO;
        if (mimeType.startsWith("application/pdf") ||
                mimeType.contains("document") ||
                mimeType.contains("text/")) return Attachment.AttachmentType.DOCUMENT;

        return Attachment.AttachmentType.OTHER;
    }
}