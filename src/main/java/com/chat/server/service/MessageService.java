package com.chat.server.service;

import com.chat.server.entity.Message;
import com.chat.server.entity.MessageStatus;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.MessageStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final ChatService chatService;
    private final ParticipantService participantService;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public Message sendMessage(Long chatId, Long senderId, String text,
                               Message.MessageType type, UUID replyToMessageUuid, List<UUID> attachmentUuids) {
        log.info("Sending message to chat: {} from user: {}", chatId, senderId);

        chatService.validateUserAccessToChat(chatId, senderId);

        Long replyToId = null;
        if (replyToMessageUuid != null) {
            Message replyTo = getMessageByUuid(replyToMessageUuid);
            replyToId = replyTo.getMessageId();
        }

        Message message = Message.builder()
                .chatId(chatId)
                .senderId(senderId)
                .messageText(text)
                .messageType(type)
                .replyToMessageId(replyToId)
                .build();

        Message savedMessage = messageRepository.save(message);
        log.info("Message sent with id: {}", savedMessage.getMessageId());

        // Обновляем последнее сообщение в чате
        chatService.updateLastMessage(chatId, savedMessage.getMessageId(), text, senderId);

        // Создаем статусы для всех участников
        List<Long> participantIds = chatService.getChatParticipants(chatId);
        for (Long participantId : participantIds) {
            MessageStatus.DeliveryStatus status = participantId.equals(senderId)
                    ? MessageStatus.DeliveryStatus.READ
                    : MessageStatus.DeliveryStatus.SENT;

            MessageStatus messageStatus = MessageStatus.builder()
                    .messageId(savedMessage.getMessageId())
                    .userId(participantId)
                    .status(status)
                    .build();

            if (status == MessageStatus.DeliveryStatus.READ) {
                messageStatus.markAsRead();
            }

            messageStatusRepository.save(messageStatus);
        }

        // Отправляем push уведомления
        pushNotificationService.sendMessageNotification(savedMessage, participantIds);

        return savedMessage;
    }

    @Transactional(readOnly = true)
    public Page<Message> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        log.debug("Fetching messages for chat: {}, user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        return messageRepository.findMessagesByChatId(chatId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Message> getMessagesBeforeMessage(Long chatId, Long userId, UUID beforeMessageUuid, int limit) {
        log.debug("Fetching messages before message: {} in chat: {}", beforeMessageUuid, chatId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message beforeMessage = getMessageByUuid(beforeMessageUuid);
        return messageRepository.findMessagesBefore(chatId, beforeMessage.getCreatedAt(), limit);
    }

    @Transactional(readOnly = true)
    public List<Message> getMessagesAfter(Long chatId, Long userId, LocalDateTime afterTime) {
        log.debug("Fetching messages after: {} in chat: {}", afterTime, chatId);

        chatService.validateUserAccessToChat(chatId, userId);

        return messageRepository.findMessagesAfter(chatId, afterTime);
    }

    @Transactional(readOnly = true)
    public long getTotalMessagesCount(Long chatId, Long userId) {
        chatService.validateUserAccessToChat(chatId, userId);
        return messageRepository.countMessagesInChat(chatId);
    }

    @Transactional(readOnly = true)
    public Message getMessageById(Long messageId) {
        log.debug("Fetching message by id: {}", messageId);
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found: " + messageId));
    }

    @Transactional(readOnly = true)
    public Message getMessageByUuid(UUID messageUuid) {
        log.debug("Fetching message by uuid: {}", messageUuid);
        return messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new NotFoundException("Message not found with uuid: " + messageUuid));
    }

    @Transactional
    public Message editMessage(UUID messageUuid, Long userId, String newText) {
        log.info("Editing message: {} by user: {}", messageUuid, userId);

        Message message = getMessageByUuid(messageUuid);

        if (!message.getSenderId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own messages");
        }

        chatService.validateUserAccessToChat(message.getChatId(), userId);

        // Сохраняем историю редактирования
        String editHistory = message.getEditHistory();
        String newEditEntry = String.format("{\"timestamp\":\"%s\",\"text\":\"%s\"}",
                LocalDateTime.now(), message.getMessageText());
        message.setEditHistory(editHistory == null ? newEditEntry : editHistory + "|" + newEditEntry);

        message.setMessageText(newText);
        message.setIsEdited(true);

        Message updatedMessage = messageRepository.save(message);
        log.info("Message edited: {}", messageUuid);

        return updatedMessage;
    }

    @Transactional
    public void deleteMessage(UUID messageUuid, Long userId, boolean hardDelete) {
        log.info("Deleting message: {} by user: {}, hardDelete: {}", messageUuid, userId, hardDelete);

        Message message = getMessageByUuid(messageUuid);

        if (!message.getSenderId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own messages");
        }

        chatService.validateUserAccessToChat(message.getChatId(), userId);

        if (hardDelete) {
            messageStatusRepository.deleteByMessageId(message.getMessageId());
            messageRepository.deleteById(message.getMessageId());
            log.info("Message hard deleted: {}", messageUuid);
        } else {
            message.setIsDeleted(true);
            message.setDeletedAt(LocalDateTime.now());
            message.setDeletedBy(userId);
            message.setMessageText("[Message deleted]");
            messageRepository.save(message);
            log.info("Message soft deleted: {}", messageUuid);
        }
    }

    @Transactional
    public Message forwardMessage(UUID originalMessageUuid, Long targetChatId, Long userId) {
        log.info("Forwarding message: {} to chat: {} by user: {}", originalMessageUuid, targetChatId, userId);

        Message original = getMessageByUuid(originalMessageUuid);

        chatService.validateUserAccessToChat(targetChatId, userId);

        Message forwarded = Message.builder()
                .chatId(targetChatId)
                .senderId(userId)
                .messageText(original.getMessageText())
                .messageType(original.getMessageType())
                .forwardedFromMessageId(original.getMessageId())
                .forwardedFromUserId(original.getSenderId())
                .build();

        Message savedMessage = messageRepository.save(forwarded);
        log.info("Message forwarded: {}", savedMessage.getMessageId());

        chatService.updateLastMessage(targetChatId, savedMessage.getMessageId(), savedMessage.getMessageText(), userId);

        return savedMessage;
    }

    @Transactional(readOnly = true)
    public Page<Message> searchMessages(Long userId, String query, Pageable pageable) {
        log.debug("Searching messages for user: {} with query: {}", userId, query);

        List<Long> chatIds = participantService.getUserChatIds(userId);
        return messageRepository.searchMessagesInChats(chatIds, query, pageable);
    }

    // ==================== Методы для работы с флагами сообщений ====================

    @Transactional
    public void updateMessageAttachmentsFlag(Long messageId, boolean hasAttachments) {
        log.debug("Updating attachments flag for message: {} to {}", messageId, hasAttachments);

        Message message = getMessageById(messageId);
        message.setHasAttachments(hasAttachments);
        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<Message> searchMessagesInChat(Long chatId, Long userId, String keyword, int limit) {
        log.debug("Searching messages in chat: {} for keyword: {}", chatId, keyword);

        chatService.validateUserAccessToChat(chatId, userId);

        return messageRepository.searchMessagesInChat(chatId, keyword, limit);
    }

    @Transactional
    public void pinMessage(Long chatId, UUID messageUuid, Long userId) {
        log.info("Pinning message: {} in chat: {} by user: {}", messageUuid, chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message message = getMessageByUuid(messageUuid);

        if (!message.getChatId().equals(chatId)) {
            throw new IllegalArgumentException("Message does not belong to this chat");
        }

        message.setIsPinned(true);
        messageRepository.save(message);
    }

    @Transactional
    public void unpinMessage(Long chatId, UUID messageUuid, Long userId) {
        log.info("Unpinning message: {} in chat: {} by user: {}", messageUuid, chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message message = getMessageByUuid(messageUuid);
        message.setIsPinned(false);
        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<Message> getPinnedMessages(Long chatId) {
        return messageRepository.findPinnedMessages(chatId);
    }
}