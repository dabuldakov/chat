package com.chat.server.service;

import com.chat.server.dto.request.UpdateChatRequestDto;
import com.chat.server.dto.response.ChatDetailsResponseDto;
import com.chat.server.dto.response.ChatResponseDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Participant;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final ParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;

    private final Map<UUID, Long> uuidToIdCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<Chat> getUserChats(Long userId) {
        log.debug("Fetching chats for user: {}", userId);
        return chatRepository.findChatsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<ChatResponseDto> getUserChatsWithDetails(Long userId) {
        log.debug("Fetching chats with details for user: {}", userId);
        List<Chat> chats = getUserChats(userId);

        return chats.stream().map(chat -> {
            List<UUID> participantIds = participantRepository.findUserIdsByChatId(chat.getChatId());
            return ChatResponseDto.fromEntity(chat, participantIds);
        }).collect(Collectors.toList());
    }

    @Transactional
    public ChatResponseDto createPrivateChat(Long user1Id, Long user2Id) {
        log.info("Creating private chat between users: {} and {}", user1Id, user2Id);

        if (user1Id.equals(user2Id)) {
            throw new ConflictException("Cannot create chat with yourself");
        }

        Optional<Chat> existingChat = chatRepository.findPrivateChatBetweenUsers(user1Id, user2Id);
        if (existingChat.isPresent()) {
            log.info("Private chat already exists: {}", existingChat.get().getChatId());
            return ChatResponseDto.fromEntity(existingChat.get(),
                    participantRepository.findUserIdsByChatId(existingChat.get().getChatId()));
        }

        Chat chat = Chat.builder()
                .chatType(Chat.ChatType.PRIVATE)
                .createdBy(user1Id)
                .build();

        Chat savedChat = chatRepository.save(chat);
        log.info("Private chat created with id: {}", savedChat.getChatId());

        addParticipant(savedChat.getChatId(), user1Id, Participant.ParticipantRole.OWNER);
        addParticipant(savedChat.getChatId(), user2Id, Participant.ParticipantRole.MEMBER);

        return ChatResponseDto.fromEntity(savedChat, participantRepository.findUserIdsByChatId(savedChat.getChatId()));
    }

    @Transactional
    public ChatResponseDto createGroupChat(String title, Long creatorId, List<Long> memberIds) {
        log.info("Creating group chat with title: {} by user: {}", title, creatorId);

        var chat = Chat.builder()
                .chatType(Chat.ChatType.GROUP)
                .title(title)
                .createdBy(creatorId)
                .build();

        var savedChat = chatRepository.save(chat);
        log.info("Group chat created with id: {}", savedChat.getChatId());

        addParticipant(savedChat.getChatId(), creatorId, Participant.ParticipantRole.OWNER);
        memberIds.forEach(memberId -> addParticipant(savedChat.getChatId(), memberId, Participant.ParticipantRole.MEMBER));

        return ChatResponseDto.fromEntity(
                savedChat,
                participantRepository.findUserIdsByChatId(savedChat.getChatId()));
    }

    private void addParticipant(Long chatId, Long userId, Participant.ParticipantRole role) {
        if (!participantRepository.existsByChatIdAndUserId(chatId, userId)) {
            Participant participant = Participant.builder()
                    .chatId(chatId)
                    .userId(userId)
                    .role(role)
                    .joinedAt(LocalDateTime.now())
                    .build();
            participantRepository.save(participant);
            log.info("User {} added to chat {} as {}", userId, chatId, role);
        }
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public ChatResponseDto updateChat(Long chatId, UpdateChatRequestDto request) {
        log.info("Updating chat: {}", chatId);
        var chat = getChatById(chatId);

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new ConflictException("Only group chats can be updated");
        }

        if (request.getTitle() != null) {
            chat.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            chat.setDescription(request.getDescription());
        }

        var saved = chatRepository.save(chat);

        return ChatResponseDto.fromEntity(saved, participantRepository.findUserIdsByChatId(chatId));
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public void updateAvatar(Long chatId, String avatarUrl) {
        Chat chat = getChatById(chatId);
        chat.setAvatarUrl(avatarUrl);
        chatRepository.save(chat);
        log.info("Avatar updated for chat: {}", chatId);
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public void archiveChat(Long chatId, Long userId) {
        validateUserAccessToChat(chatId, userId);
        var chat = getChatById(chatId);
        chat.setIsArchived(true);
        chatRepository.save(chat);
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public void unarchiveChat(Long chatId, Long userId) {
        validateUserAccessToChat(chatId, userId);
        Chat chat = getChatById(chatId);
        chat.setIsArchived(false);
        chatRepository.save(chat);
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public void updateChatTimestamp(Long chatId) {
        Chat chat = getChatById(chatId);
        chat.setUpdatedAt(LocalDateTime.now());
        chatRepository.save(chat);
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public void updateLastMessage(Long chatId, Long messageId, String messageText, Long senderId) {
        Chat chat = getChatById(chatId);
        chat.setLastMessageId(messageId);
        chat.setLastMessageText(messageText);
        chat.setLastMessageSenderId(senderId);
        chat.setMessageCount(chat.getMessageCount() + 1);
        chat.setUpdatedAt(LocalDateTime.now());
        chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "chats", key = "#chatId")
    public Chat getChatById(Long chatId) {
        log.debug("Fetching chat by id: {}", chatId);
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Chat not found: " + chatId));
    }

    @Transactional(readOnly = true)
    public Long getChatIdByUuid(UUID chatUuid) {
        if (uuidToIdCache.containsKey(chatUuid)) {
            return uuidToIdCache.get(chatUuid);
        }

        var chat = chatRepository.findByChatUuid(chatUuid)
                .orElseThrow(() -> new NotFoundException("Chat not found with uuid: " + chatUuid));

        uuidToIdCache.put(chatUuid, chat.getChatId());
        return chat.getChatId();
    }

    @Transactional(readOnly = true)
    public ChatDetailsResponseDto getChatDetails(Long chatId, Long userId) {
        var chat = getChatById(chatId);
        var participantIds = participantRepository.findUserIdsByChatId(chatId);

        var unreadCount = getUnreadMessagesCount(chatId, userId);

        return ChatDetailsResponseDto.toDto(chat, participantIds, unreadCount);
    }

    @Transactional(readOnly = true)
    public void validateUserAccessToChat(Long chatId, Long userId) {
        boolean hasAccess = participantRepository.existsByChatIdAndUserId(chatId, userId);
        if (!hasAccess) {
            log.warn("User {} attempted to access chat {} without permission", userId, chatId);
            throw new AccessDeniedException("User does not have access to this chat");
        }
    }

    @Transactional(readOnly = true)
    public List<Long> getChatParticipants(Long chatId) {
        return participantRepository.findAllByChatId(chatId).stream()
                .map(Participant::getUserId).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadMessagesCount(Long chatId, Long userId) {
        var participant = participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new NotFoundException("Participant not found"));

        var lastReadMessageId = participant.getLastReadMessageId();

        if (lastReadMessageId == null) {
            return messageRepository.countMessagesInChat(chatId);
        }

        return messageRepository.countMessagesAfterId(chatId, lastReadMessageId);
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount(Long userId) {
        var chatIds = participantRepository.findAllByChatId(userId).stream()
                .map(Participant::getChatId).toList();
        long totalUnread = 0;

        for (Long chatId : chatIds) {
            totalUnread += getUnreadMessagesCount(chatId, userId);
        }

        return totalUnread;
    }

    @Transactional(readOnly = true)
    public Optional<Chat> findPrivateChatBetweenUsers(Long user1Id, Long user2Id) {
        return chatRepository.findPrivateChatBetweenUsers(user1Id, user2Id);
    }

    @Transactional
    @CacheEvict(value = "chats", key = "#chatId")
    public void deleteChat(Long chatId, Long userId) {
        log.warn("Deleting chat: {} by user: {}", chatId, userId);

        Chat chat = getChatById(chatId);

        if (!chat.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("Only chat creator can delete the chat");
        }

        participantRepository.deleteAll(participantRepository.findAllByChatId(chatId));
        chatRepository.delete(chat);
        log.info("Chat deleted: {}", chatId);
    }
}