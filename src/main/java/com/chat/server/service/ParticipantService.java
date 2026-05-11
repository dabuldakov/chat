package com.chat.server.service;

import com.chat.server.dto.response.ParticipantInfoDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final ChatService chatService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<Participant> getChatParticipants(Long chatId) {
        log.debug("Fetching participants for chat: {}", chatId);
        return participantRepository.findAllByChatId(chatId);
    }

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

        return participants.stream()
                .map(p -> ParticipantInfoDto.fromEntity(p, userMap.get(p.getUserId())))
                .toList();
    }

    @Transactional
    public void addParticipantsToGroup(Long chatId, List<Long> userIds, Long requesterId) {
        log.info("Adding {} participants to group chat: {} by user: {}", userIds.size(), chatId, requesterId);

        Chat chat = chatService.getChatById(chatId);

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new ConflictException("Can only add participants to group chats");
        }

        chatService.validateUserAccessToChat(chatId, requesterId);

        for (Long userId : userIds) {
            if (!participantRepository.existsByChatIdAndUserId(chatId, userId)) {
                Participant participant = Participant.builder()
                        .chatId(chatId)
                        .userId(userId)
                        .role(Participant.ParticipantRole.MEMBER)
                        .joinedAt(LocalDateTime.now())
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

        if (!chat.getCreatedBy().equals(requesterId)) {
            throw new AccessDeniedException("Only group creator can remove participants");
        }

        if (chat.getCreatedBy().equals(userId)) {
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

        if (chat.getCreatedBy().equals(userId)) {
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

        if (!chat.getCreatedBy().equals(requesterId)) {
            throw new AccessDeniedException("Only group creator can change roles");
        }

        if (chat.getCreatedBy().equals(targetUserId)) {
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
            participant.setMutedUntil(LocalDateTime.now().plusHours(durationHours));
        } else {
            participant.setMutedUntil(LocalDateTime.now().plusYears(100)); // Навсегда
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