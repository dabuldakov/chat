package com.chat.server.service;

import com.chat.server.dto.response.SyncResponseDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final ChatService chatService;
    private final MessageService messageService;
    private final ParticipantService participantService;

    @Transactional(readOnly = true)
    public SyncResponseDto syncUserData(Long userId, LocalDateTime lastSyncTime) {
        log.info("Syncing data for user: {}, since: {}", userId, lastSyncTime);

        SyncResponseDto response = new SyncResponseDto();
        response.setSyncTime(LocalDateTime.now());

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

        // Получаем новые сообщения
        Map<Long, List<Message>> newMessages = new HashMap<>();
        for (Long chatId : userChatIds) {
            List<Message> messages = messageService.getMessagesAfter(chatId, userId, lastSyncTime);
            if (!messages.isEmpty()) {
                newMessages.put(chatId, messages);
            }
        }
        response.setNewMessages(newMessages);

        // Получаем изменения в участниках
        Map<Long, List<Long>> newParticipants = new HashMap<>();
        // ... логика получения новых участников

        log.info("Sync completed for user: {}, found {} chats with new messages",
                userId, newMessages.size());

        return response;
    }

    @Transactional(readOnly = true)
    public SyncResponseDto syncMessagesOnly(Long userId, LocalDateTime lastSyncTime) {
        SyncResponseDto response = new SyncResponseDto();
        response.setSyncTime(LocalDateTime.now());

        List<Long> userChatIds = participantService.getUserChatIds(userId);

        Map<Long, List<Message>> newMessages = new HashMap<>();
        for (Long chatId : userChatIds) {
            List<Message> messages = messageService.getMessagesAfter(chatId, userId, lastSyncTime);
            if (!messages.isEmpty()) {
                newMessages.put(chatId, messages);
            }
        }
        response.setNewMessages(newMessages);

        return response;
    }

    @Transactional(readOnly = true)
    public SyncResponseDto syncChatsOnly(Long userId, LocalDateTime lastSyncTime) {
        SyncResponseDto response = new SyncResponseDto();
        response.setSyncTime(LocalDateTime.now());

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
        for (Long chatId : chatIds) {
            totalMessages += messageService.getTotalMessagesCount(chatId, userId);
        }

        return new SyncStatusResponse(
                LocalDateTime.now().minusMinutes(5), // lastFullSync
                LocalDateTime.now().minusSeconds(30), // lastMessagesSync
                (long) chatIds.size(),
                totalMessages,
                0 // pendingUploads
        );
    }

    record SyncStatusResponse(
            LocalDateTime lastFullSync,
            LocalDateTime lastMessagesSync,
            long totalChats,
            long totalMessages,
            long pendingUploads
    ) {}
}