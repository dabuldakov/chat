package com.chat.server.message;

import com.chat.server.chat.Participant;
import com.chat.server.exception.BadRequestException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.chat.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.chat.server.chat.ChatService;
import com.chat.server.user.User;
/**
 * Статусы доставки/прочтения на основе watermark-ов участника
 * ({@code participants.last_read_message_id}, {@code last_delivered_message_id}),
 * а не построчных записей на получателя. Это исключает fan-out на запись:
 * отправка в группу на 1000 человек не создаёт 1000 строк статусов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatusService {

    private final ParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ChatService chatService;

    @Transactional
    public void markMessagesAsRead(Long chatId, Long userId, UUID upToMessageUuid) {
        log.debug("Marking messages as read in chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message upToMessage = messageRepository.findByMessageUuid(upToMessageUuid)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (!upToMessage.getChatId().equals(chatId)) {
            throw new BadRequestException("Message does not belong to this chat");
        }

        advanceRead(chatId, userId, upToMessage.getMessageId());
    }

    @Transactional
    public void markMessageAsRead(UUID messageUuid, Long userId) {
        Message message = getMessageByUuid(messageUuid);
        advanceRead(message.getChatId(), userId, message.getMessageId());
    }

    @Transactional
    public void markMessageAsDelivered(UUID messageUuid, Long userId) {
        Message message = getMessageByUuid(messageUuid);
        participantRepository.advanceDeliveredWatermark(
                message.getChatId(), userId, message.getMessageId(), now());
    }

    @Transactional
    public void updateLastReadMessage(Long chatId, Long userId, Long messageId) {
        chatService.validateUserAccessToChat(chatId, userId);
        advanceRead(chatId, userId, messageId);
    }

    @Transactional(readOnly = true)
    public List<MessageStatusDto> getMessageStatuses(UUID messageUuid, Long chatId, Long userId) {
        log.debug("Getting statuses for message: {} in chat: {}", messageUuid, chatId);

        chatService.validateUserAccessToChat(chatId, userId);

        Message message = messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (!message.getChatId().equals(chatId)) {
            throw new BadRequestException("Message does not belong to this chat");
        }

        return participantRepository.findAllByChatId(chatId).stream()
                .map(p -> toDto(p, message))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryStatusDto getMessageStatusForUser(UUID messageUuid, Long userId) {
        Message message = getMessageByUuid(messageUuid);
        Participant participant = participantRepository
                .findByChatIdAndUserId(message.getChatId(), userId)
                .orElse(null);
        return deriveStatus(participant, message, userId);
    }

    @Transactional(readOnly = true)
    public boolean isMessageReadByAll(UUID messageUuid, Long chatId) {
        Message message = getMessageByUuid(messageUuid);
        long total = participantRepository.countByChatId(chatId);
        if (total == 0) {
            return false;
        }
        long readers = participantRepository.countReaders(chatId, message.getMessageId(), message.getSenderId());
        return readers == total;
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForUser(Long chatId, Long userId) {
        Long lastRead = participantRepository.findByChatIdAndUserId(chatId, userId)
                .map(Participant::getLastReadMessageId)
                .orElse(null);
        return messageRepository.countUnreadMessagesForUser(chatId, userId, lastRead);
    }

    private void advanceRead(Long chatId, Long userId, Long messageId) {
        if (!participantRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new NotFoundException("User is not a participant of this chat");
        }
        participantRepository.advanceReadWatermark(chatId, userId, messageId, now());
    }

    private Message getMessageByUuid(UUID messageUuid) {
        return messageRepository.findByMessageUuid(messageUuid)
                .orElseThrow(() -> new NotFoundException("Message not found"));
    }

    private MessageStatusDto toDto(Participant participant, Message message) {
        DeliveryStatusDto status = deriveStatus(participant, message, participant.getUserId());
        boolean delivered = status == DeliveryStatusDto.DELIVERED || status == DeliveryStatusDto.READ;
        return MessageStatusDto.builder()
                .userId(participant.getUserId())
                .status(status)
                .deliveredAt(delivered ? participant.getLastDeliveredAt() : null)
                .readAt(status == DeliveryStatusDto.READ ? participant.getLastReadAt() : null)
                .build();
    }

    private DeliveryStatusDto deriveStatus(Participant participant, Message message, Long userId) {
        if (participant == null) {
            return DeliveryStatusDto.SENT;
        }
        // Отправитель считается прочитавшим собственное сообщение.
        if (userId.equals(message.getSenderId())) {
            return DeliveryStatusDto.READ;
        }
        if (participant.getLastReadMessageId() != null
                && participant.getLastReadMessageId() >= message.getMessageId()) {
            return DeliveryStatusDto.READ;
        }
        if (participant.getLastDeliveredMessageId() != null
                && participant.getLastDeliveredMessageId() >= message.getMessageId()) {
            return DeliveryStatusDto.DELIVERED;
        }
        return DeliveryStatusDto.SENT;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
