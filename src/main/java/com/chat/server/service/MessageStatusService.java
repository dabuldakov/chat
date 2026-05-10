package com.chat.server.service;

import com.chat.server.dto.response.MessageStatusDto;
import com.chat.server.entity.Message;
import com.chat.server.entity.MessageStatus;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.MessageStatusRepository;
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
    private final ChatService chatService;

    @Transactional
    public void markMessagesAsRead(Long chatId, Long userId, UUID upToMessageUuid) {
        log.debug("Marking messages as read in chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message upToMessage = messageRepository.findByMessageUuid(upToMessageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        List<MessageStatus> statuses = messageStatusRepository.findUnreadMessagesInChat(chatId, userId);

        for (MessageStatus status : statuses) {
            if (status.getMessageId() <= upToMessage.getMessageId()) {
                status.markAsRead();
                messageStatusRepository.save(status);
            }
        }

        // Обновляем last_read_message_id в Participant
        updateLastReadMessage(chatId, userId, upToMessage.getMessageId());
    }

    @Transactional
    public void updateLastReadMessage(Long chatId, Long userId, Long messageId) {
        chatService.validateUserAccessToChat(chatId, userId);

        messageStatusRepository.updateLastReadMessage(chatId, userId, messageId);
    }

    @Transactional
    public void markMessageAsDelivered(Long messageId, Long userId) {
        messageStatusRepository.findByMessageIdAndUserId(messageId, userId)
                .ifPresent(status -> {
                    if (status.getStatus() == MessageStatus.DeliveryStatus.SENT) {
                        status.markAsDelivered();
                        messageStatusRepository.save(status);
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<MessageStatusDto> getMessageStatuses(UUID messageUuid) {
        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        List<MessageStatus> statuses = messageStatusRepository.findByMessageId(message.getMessageId());

        return statuses.stream()
                .map(s -> MessageStatusDto.builder()
                        .userId(s.getUserId())
                        .status(s.getStatus().name())
                        .deliveredAt(s.getDeliveredAt())
                        .readAt(s.getReadAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MessageStatus.DeliveryStatus getMessageStatusForUser(Long messageId, Long userId) {
        return messageStatusRepository.findByMessageIdAndUserId(messageId, userId)
                .map(MessageStatus::getStatus)
                .orElse(MessageStatus.DeliveryStatus.SENT);
    }

    @Transactional(readOnly = true)
    public boolean isMessageReadByAll(Long messageId, Long chatId) {
        long participantCount = chatService.getChatParticipants(chatId).size();
        long readCount = messageStatusRepository.countReadStatuses(messageId);
        return readCount >= participantCount;
    }
}