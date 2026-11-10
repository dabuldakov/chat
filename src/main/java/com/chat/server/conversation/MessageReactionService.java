package com.chat.server.conversation;

import com.chat.server.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Реакции на сообщения. Храним по одной строке на (сообщение, пользователь,
 * эмодзи); повторный тап снимает реакцию.
 */
@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private static final int MAX_EMOJI_LENGTH = 16;

    private final MessageReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final ChatService chatService;

    @Transactional
    public List<ReactionDto> toggle(UUID messageUuid, Long userId, String emoji) {
        String clean = normalize(emoji);
        Message message = messageService.getMessageByUuid(messageUuid);
        chatService.validateUserAccessToChat(message.getChatId(), userId);

        Optional<MessageReaction> existing = reactionRepository
                .findByMessageIdAndUserIdAndEmoji(message.getMessageId(), userId, clean);
        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
        } else {
            reactionRepository.save(MessageReaction.builder()
                    .messageId(message.getMessageId())
                    .userId(userId)
                    .emoji(clean)
                    .build());
        }
        return forMessage(message.getMessageId(), userId);
    }

    /** Подставляет реакции в DTO пачкой, чтобы не делать запрос на каждое сообщение. */
    @Transactional(readOnly = true)
    public void enrich(List<Message> messages, List<MessageDto> dtos, Long userId) {
        List<Long> ids = messages.stream()
                .map(Message::getMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<MessageReaction>> byMessage = reactionRepository.findByMessageIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));
        for (int i = 0; i < messages.size(); i++) {
            dtos.get(i).setReactions(
                    aggregate(byMessage.get(messages.get(i).getMessageId()), userId)
            );
        }
    }

    private List<ReactionDto> forMessage(Long messageId, Long userId) {
        return aggregate(reactionRepository.findByMessageIdIn(List.of(messageId)), userId);
    }

    private List<ReactionDto> aggregate(List<MessageReaction> reactions, Long userId) {
        if (reactions == null || reactions.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> mine = new HashSet<>();
        for (MessageReaction reaction : reactions) {
            counts.merge(reaction.getEmoji(), 1, Integer::sum);
            if (Objects.equals(reaction.getUserId(), userId)) {
                mine.add(reaction.getEmoji());
            }
        }
        return counts.entrySet().stream()
                .map(entry -> ReactionDto.builder()
                        .emoji(entry.getKey())
                        .count(entry.getValue())
                        .mine(mine.contains(entry.getKey()))
                        .build())
                .toList();
    }

    private String normalize(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new BadRequestException("Emoji is required");
        }
        String clean = emoji.trim();
        if (clean.length() > MAX_EMOJI_LENGTH) {
            throw new BadRequestException("Emoji is too long");
        }
        return clean;
    }
}
