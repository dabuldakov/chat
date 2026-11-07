package com.chat.server.conversation;

import com.chat.server.identity.User;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.identity.PresenceInfo;
import com.chat.server.identity.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.chat.server.contacts.ContactService;
import com.chat.server.identity.UserService;
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final ChatService chatService;
    private final UserService userService;
    private final PresenceService presenceService;
    private final ContactService contactService;

    @Transactional(readOnly = true)
    public List<Participant> getChatParticipants(Long chatId) {
        log.debug("Fetching participants for chat: {}", chatId);
        return participantRepository.findAllByChatId(chatId);
    }

    /**
     * Пользователи читаются через findAllByIds, то есть мимо 10-минутного кэша,
     * поэтому last_seen_at здесь свежий, а «онлайн» пересчитывается по TTL.
     */
    @Transactional(readOnly = true)
    public List<ParticipantInfoDto> getChatParticipantsWithDetails(Long chatId) {
        log.debug("Fetching participants with details for chat: {}", chatId);

        List<Participant> participants = getChatParticipants(chatId);
        List<Long> userIds = participants.stream()
                .map(Participant::getUserId)
                .toList();

        List<User> users = userService.findAllByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        // Статус берём отдельным запросом, а не из сущности User: кэш
        // пользователей живёт 10 минут, и «в сети» не должен от него зависеть.
        Map<Long, PresenceInfo> presenceByUserId = presenceService.presenceByUserIds(userIds);

        return participants.stream()
                .map(p -> {
                    User user = userMap.get(p.getUserId());
                    PresenceInfo presence = presenceByUserId.getOrDefault(
                            p.getUserId(), PresenceInfo.unknown());
                    return ParticipantInfoDto.fromEntity(p, user, presence);
                })
                .toList();
    }

    /**
     * Кандидаты для добавления в группу: контакты пользователя плюс
     * собеседники из личных чатов, кроме уже добавленных и самого себя.
     *
     * Контакты — не единственный источник: человек, с которым уже есть
     * переписка, но который не сохранён в контактах (или чья запись контакта
     * устарела), иначе не попадал в список и его нельзя было добавить.
     * Собеседники из чатов закрывают этот сценарий.
     */
    @Transactional(readOnly = true)
    public List<ParticipantCandidateDto> getCandidates(Long chatId, Long requesterId) {
        chatService.validateUserAccessToChat(chatId, requesterId);

        java.util.Set<Long> contactIds = new java.util.LinkedHashSet<>(
                contactService.getContactUserIds(requesterId));
        java.util.Set<Long> partnerIds = new java.util.LinkedHashSet<>(
                participantRepository.findPrivateChatPartnerIds(requesterId));

        java.util.Set<Long> candidateIds = new java.util.LinkedHashSet<>();
        candidateIds.addAll(contactIds);
        candidateIds.addAll(partnerIds);

        // Не предлагаем себя и тех, кто уже в группе.
        candidateIds.remove(requesterId);
        participantRepository.findAllByChatId(chatId)
                .forEach(p -> candidateIds.remove(p.getUserId()));

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> users = userService.findAllByIds(List.copyOf(candidateIds)).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        return candidateIds.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .map(user -> ParticipantCandidateDto.fromUser(
                        user, contactIds.contains(user.getUserId())))
                .sorted(java.util.Comparator.comparing(
                        this::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String displayName(ParticipantCandidateDto candidate) {
        if (candidate.getFullName() != null && !candidate.getFullName().isBlank()) {
            return candidate.getFullName();
        }
        if (candidate.getUsername() != null && !candidate.getUsername().isBlank()) {
            return candidate.getUsername();
        }
        return "";
    }

    @Transactional
    public void addParticipantsToGroup(Long chatId, List<Long> userIds, Long requesterId) {
        log.info("Adding {} participants to group chat: {} by user: {}", userIds.size(), chatId, requesterId);

        Chat chat = chatService.getChatById(chatId);

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new ConflictException("Can only add participants to group chats");
        }

        chatService.validateUserAccessToChat(chatId, requesterId);

        if (!Objects.equals(chat.getCreatedBy(), requesterId)) {
            throw new AccessDeniedException("Only group creator can add participants");
        }

        for (Long userId : userIds) {
            if (!participantRepository.existsByChatIdAndUserId(chatId, userId)) {
                Participant participant = Participant.builder()
                        .chatId(chatId)
                        .userId(userId)
                        .userUUID(userService.getUserById(userId).getUserUuid())
                        .role(Participant.ParticipantRole.MEMBER)
                        .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build();
                participantRepository.save(participant);
                log.info("User {} added to chat {}", userId, chatId);
            }
        }
    }

    @Transactional
    public void removeParticipantFromGroup(Long chatId, Long userId, Long requesterId) {
        log.info("Removing user {} from group chat: {} by user: {}", userId, chatId, requesterId);

        Chat chat = chatService.getChatById(chatId);

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new ConflictException("Can only remove participants from group chats");
        }

        if (!Objects.equals(chat.getCreatedBy(), requesterId)) {
            throw new AccessDeniedException("Only group creator can remove participants");
        }

        if (Objects.equals(chat.getCreatedBy(), userId)) {
            throw new ConflictException("Cannot remove group creator");
        }

        if (!participantRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new NotFoundException("User is not a participant of this chat");
        }

        participantRepository.deleteByChatIdAndUserId(chatId, userId);
        log.info("User {} removed from chat {}", userId, chatId);
    }

    @Transactional
    public void leaveGroup(Long chatId, Long userId) {
        log.info("User {} leaving group chat: {}", userId, chatId);

        Chat chat = chatService.getChatById(chatId);

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new ConflictException("Cannot leave private chat");
        }

        if (Objects.equals(chat.getCreatedBy(), userId)) {
            throw new ConflictException("Group creator cannot leave, consider deleting the chat instead");
        }

        if (!participantRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new NotFoundException("User is not a participant of this chat");
        }

        participantRepository.deleteByChatIdAndUserId(chatId, userId);
        log.info("User {} left chat {}", userId, chatId);
    }

    @Transactional
    public void updateParticipantRole(Long chatId, Long targetUserId, Long requesterId, Participant.ParticipantRole newRole) {
        log.info("Updating role of user {} in chat {} to {} by {}", targetUserId, chatId, newRole, requesterId);

        Chat chat = chatService.getChatById(chatId);

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new ConflictException("Only group chats have roles");
        }

        if (!Objects.equals(chat.getCreatedBy(), requesterId)) {
            throw new AccessDeniedException("Only group creator can change roles");
        }

        if (Objects.equals(chat.getCreatedBy(), targetUserId)) {
            throw new ConflictException("Cannot change role of group creator");
        }

        Participant participant = participantRepository.findByChatIdAndUserId(chatId, targetUserId)
                .orElseThrow(() -> new NotFoundException("User is not a participant"));

        participant.setRole(newRole);
        participantRepository.save(participant);
        log.info("Role updated for user {} in chat {}", targetUserId, chatId);
    }

    @Transactional
    public void muteChat(Long chatId, Long userId, Integer durationHours) {
        log.info("Muting chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Participant participant = participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new NotFoundException("Participant not found"));

        if (durationHours != null && durationHours > 0) {
            participant.setMutedUntil(LocalDateTime.now(ZoneOffset.UTC).plusHours(durationHours));
        } else {
            participant.setMutedUntil(LocalDateTime.now(ZoneOffset.UTC).plusYears(100)); // Навсегда
        }

        participantRepository.save(participant);
    }

    @Transactional
    public void unmuteChat(Long chatId, Long userId) {
        log.info("Unmuting chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Participant participant = participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new NotFoundException("Participant not found"));

        participant.setMutedUntil(null);
        participantRepository.save(participant);
    }

    @Transactional
    public void pinChat(Long chatId, Long userId) {
        log.info("Pinning chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Participant participant = participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new NotFoundException("Participant not found"));

        participant.setIsPinned(true);
        participantRepository.save(participant);
    }

    @Transactional
    public void unpinChat(Long chatId, Long userId) {
        log.info("Unpinning chat: {} for user: {}", chatId, userId);

        chatService.validateUserAccessToChat(chatId, userId);

        Participant participant = participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new NotFoundException("Participant not found"));

        participant.setIsPinned(false);
        participantRepository.save(participant);
    }

    @Transactional(readOnly = true)
    public Participant getParticipant(Long chatId, Long userId) {
        return participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new NotFoundException("Participant not found"));
    }

    @Transactional(readOnly = true)
    public List<Long> getUserChatIds(Long userId) {
        log.debug("Fetching chat ids for user: {}", userId);
        return participantRepository.findChatIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getParticipantsCount(Long chatId) {
        return participantRepository.countByChatId(chatId);
    }
}