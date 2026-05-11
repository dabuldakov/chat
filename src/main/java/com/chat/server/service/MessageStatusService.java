package com.chat.server.service;

import com.chat.server.dto.response.DeliveryStatusDto;
import com.chat.server.dto.response.MessageStatusDto;
import com.chat.server.entity.Message;
import com.chat.server.entity.MessageStatus;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.MessageStatusRepository;
import com.chat.server.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatusService {

    private final MessageStatusRepository messageStatusRepository;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;
    private final ChatService chatService;

    @Transactional
    public void createStatusesForMessage(Long messageId, List<Long> participantIds, Long senderId) {
        log.debug("Creating statuses for message: {} for {} participants", messageId, participantIds.size());

        for (Long userId : participantIds) {
            MessageStatus.DeliveryStatus status = userId.equals(senderId)
                    ? MessageStatus.DeliveryStatus.READ
                    : MessageStatus.DeliveryStatus.SENT;

            MessageStatus messageStatus = MessageStatus.builder()
                    .messageId(messageId)
                    .userId(userId)
                    .status(status)
                    .build();

            if (status == MessageStatus.DeliveryStatus.READ) {
                messageStatus.markAsRead();
            }

            messageStatusRepository.save(messageStatus);
        }
    }

    @Transactional
    public void markMessageAsDelivered(UUID messageUuid, Long userId) {
        log.debug("Marking message as delivered: {} for user: {}", messageUuid, userId);

        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        messageStatusRepository.markAsDelivered(
                message.getMessageId(),
                userId,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void markMessageAsRead(UUID messageUuid, Long userId) {
        log.debug("Marking message as read: {} for user: {}", messageUuid, userId);

        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        messageStatusRepository.markAsRead(
                message.getMessageId(),
                userId,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void markMessagesAsRead(Long chatId, Long userId, UUID upToMessageUuid) {
        log.debug("Marking messages as read in chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message upToMessage = messageRepository.findByMessageUuid(upToMessageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // ⭐ Оптимизированная версия - один запрос к БД вместо цикла
        int updatedCount = messageStatusRepository.markMessagesAsReadInChat(
                chatId,
                userId,
                upToMessage.getMessageId(),
                LocalDateTime.now()
        );

        log.debug("Marked {} messages as read for user {} in chat {}", updatedCount, userId, chatId);

        // Обновляем last_read_message_id в Participant
        updateLastReadMessage(chatId, userId, upToMessage.getMessageId());
    }

    @Transactional
    public void updateLastReadMessage(Long chatId, Long userId, Long messageId) {
        log.debug("Updating last read message for user: {} in chat: {} to message: {}", userId, chatId, messageId);

        chatService.validateUserAccessToChat(chatId, userId);

        // ⭐ Теперь это работает - метод есть в ParticipantRepository
        participantRepository.updateLastReadMessage(chatId, userId, messageId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<MessageStatusDto> getMessageStatuses(UUID messageUuid) {
        log.debug("Getting statuses for message: {}", messageUuid);

        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        List<MessageStatus> statuses = messageStatusRepository.findByMessageId(message.getMessageId());

        return statuses.stream()
                .map(s -> MessageStatusDto.builder()
                        .userId(s.getUserId())
                        .status(DeliveryStatusDto.valueOf(s.getStatus().name()))
                        .deliveredAt(s.getDeliveredAt())
                        .readAt(s.getReadAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MessageStatus.DeliveryStatus getMessageStatusForUser(UUID messageUuid, Long userId) {
        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        return messageStatusRepository.findByMessageIdAndUserId(message.getMessageId(), userId)
                .map(MessageStatus::getStatus)
                .orElse(MessageStatus.DeliveryStatus.SENT);
    }

    @Transactional(readOnly = true)
    public boolean isMessageReadByAll(UUID messageUuid, Long chatId) {
        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        return messageStatusRepository.isMessageReadByAll(message.getMessageId(), chatId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForUser(Long chatId, Long userId) {
        List<MessageStatus> unreadStatuses = messageStatusRepository.findUnreadMessagesInChat(chatId, userId);
        return unreadStatuses.size();
    }
}