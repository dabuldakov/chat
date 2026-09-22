package com.chat.server.service;

import com.chat.server.dto.response.SyncResponseDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import com.chat.server.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int DEFAULT_SYNC_LIMIT = 500;

    private final ChatService chatService;
    private final ParticipantService participantService;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public SyncResponseDto syncUserData(Long userId, LocalDateTime lastSyncTime) {
        log.info("Syncing data for user: {}, since: {}", userId, lastSyncTime);

        SyncResponseDto response = new SyncResponseDto();
        response.setSyncTime(LocalDateTime.now(ZoneOffset.UTC));

        // Получаем все чаты пользователя
        List<Chat> userChats = chatService.getUserChats(userId);
        List<Long> userChatIds = userChats.stream()
                .map(Chat::getChatId)
                .toList();
        response.setChatIds(userChatIds);

        // Получаем обновленные чаты
        List<Chat> updatedChats = userChats.stream()
                .filter(chat -> chat.getUpdatedAt().isAfter(lastSyncTime))
                .toList();
        response.setUpdatedChats(updatedChats);

        // Получаем новые сообщения одним пакетным запросом по всем чатам
        response.setNewMessages(newMessagesForChats(userChatIds, lastSyncTime));

        log.info("Sync completed for user: {}, found {} chats with new messages",
                userId, response.getNewMessages() == null ? 0 : response.getNewMessages().size());

        return response;
    }

    @Transactional(readOnly = true)
    public SyncResponseDto syncMessagesOnly(Long userId, LocalDateTime lastSyncTime) {
        SyncResponseDto response = new SyncResponseDto();
        response.setSyncTime(LocalDateTime.now(ZoneOffset.UTC));

        List<Long> userChatIds = participantService.getUserChatIds(userId);

        response.setNewMessages(newMessagesForChats(userChatIds, lastSyncTime));

        return response;
    }

    @Transactional(readOnly = true)
    public SyncResponseDto syncChatsOnly(Long userId, LocalDateTime lastSyncTime) {
        SyncResponseDto response = new SyncResponseDto();
        response.setSyncTime(LocalDateTime.now(ZoneOffset.UTC));

        List<Chat> userChats = chatService.getUserChats(userId);

        List<Chat> updatedChats = userChats.stream()
                .filter(chat -> chat.getUpdatedAt().isAfter(lastSyncTime))
                .toList();
        response.setUpdatedChats(updatedChats);

        return response;
    }

    @Transactional(readOnly = true)
    public SyncStatusResponse getSyncStatus(Long userId) {
        List<Long> chatIds = participantService.getUserChatIds(userId);

        long totalMessages = 0;
        if (!chatIds.isEmpty()) {
            List<Object[]> counts = messageRepository.countMessagesInChats(chatIds);
            for (Object[] row : counts) {
                totalMessages += ((Number) row[1]).longValue();
            }
        }

        return new SyncStatusResponse(
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), // lastFullSync
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(30), // lastMessagesSync
                (long) chatIds.size(),
                totalMessages,
                0 // pendingUploads
        );
    }

    /**
     * Сообщения после :lastSyncTime для всех чатов одним запросом
     * (ROW_NUMBER + PARTITION BY chat_id, лимит на каждый чат).
     */
    private Map<Long, List<Message>> newMessagesForChats(List<Long> chatIds, LocalDateTime lastSyncTime) {
        if (chatIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Message> messages = messageRepository.findMessagesAfterForChats(
                chatIds, lastSyncTime, DEFAULT_SYNC_LIMIT);

        Map<Long, List<Message>> result = new LinkedHashMap<>();
        for (Message message : messages) {
            result.computeIfAbsent(message.getChatId(), k -> new ArrayList<>()).add(message);
        }
        return result;
    }

}