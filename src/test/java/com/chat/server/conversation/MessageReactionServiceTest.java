package com.chat.server.conversation;

import com.chat.server.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageReactionServiceTest {

    private final MessageReactionRepository repository = mock(MessageReactionRepository.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final MessageService messageService = mock(MessageService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final MessageReactionService service =
            new MessageReactionService(repository, messageRepository, messageService, chatService);

    private final UUID messageUuid = UUID.randomUUID();
    private final Message message = Message.builder()
            .messageId(10L)
            .chatId(5L)
            .senderId(1L)
            .messageType(Message.MessageType.TEXT)
            .build();

    @BeforeEach
    void setUp() {
        when(messageService.getMessageByUuid(messageUuid)).thenReturn(message);
    }

    @Test
    void addsReactionAndReturnsAggregate() {
        when(repository.findByMessageIdAndUserIdAndEmoji(10L, 2L, "👍"))
                .thenReturn(Optional.empty());
        when(repository.findByMessageIdIn(List.of(10L))).thenReturn(List.of(
                MessageReaction.builder().messageId(10L).userId(2L).emoji("👍").build()
        ));

        List<ReactionDto> result = service.toggle(messageUuid, 2L, "👍");

        assertEquals(1, result.size());
        assertEquals("👍", result.get(0).getEmoji());
        assertEquals(1, result.get(0).getCount());
        assertTrue(result.get(0).isMine());
        verify(repository).save(any(MessageReaction.class));
    }

    @Test
    void secondTapRemovesReaction() {
        MessageReaction existing = MessageReaction.builder()
                .messageId(10L).userId(2L).emoji("🔥").build();
        when(repository.findByMessageIdAndUserIdAndEmoji(10L, 2L, "🔥"))
                .thenReturn(Optional.of(existing));
        when(repository.findByMessageIdIn(List.of(10L))).thenReturn(List.of());

        List<ReactionDto> result = service.toggle(messageUuid, 2L, "🔥");

        assertTrue(result.isEmpty());
        verify(repository).delete(existing);
    }

    @Test
    void rejectsBlankAndTooLongEmoji() {
        assertThrows(BadRequestException.class, () -> service.toggle(messageUuid, 2L, " "));
        assertThrows(BadRequestException.class,
                () -> service.toggle(messageUuid, 2L, "x".repeat(17)));
    }

    @Test
    void enrichGroupsByEmojiAndMarksMine() {
        MessageDto dto = MessageDto.fromEntity(message);
        when(repository.findByMessageIdIn(List.of(10L))).thenReturn(List.of(
                MessageReaction.builder().messageId(10L).userId(1L).emoji("👍").build(),
                MessageReaction.builder().messageId(10L).userId(2L).emoji("👍").build(),
                MessageReaction.builder().messageId(10L).userId(2L).emoji("❤️").build()
        ));

        service.enrich(List.of(message), List.of(dto), 2L);

        assertEquals(List.of("👍", "❤️"),
                dto.getReactions().stream().map(ReactionDto::getEmoji).toList());
        assertEquals(2, dto.getReactions().get(0).getCount());
        assertTrue(dto.getReactions().get(0).isMine());
        assertTrue(dto.getReactions().get(1).isMine());
    }
}
