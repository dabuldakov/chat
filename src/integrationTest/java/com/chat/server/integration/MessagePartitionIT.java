package com.chat.server.integration;

import com.chat.server.chat.Chat;
import com.chat.server.message.Message;
import com.chat.server.user.User;
import com.chat.server.chat.ChatRepository;
import com.chat.server.message.MessageRepository;
import com.chat.server.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что messages действительно RANGE-партиционирована по created_at,
 * что функция автосоздания партиций идемпотентна и что свежее сообщение
 * попадает в месячную партицию, а не в messages_default.
 */
class MessagePartitionIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;

    private Long chatId;
    private Long senderId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .username("part").email("part@example.com").passwordHash("h")
                .isOnline(false).isDeleted(false).build());
        senderId = user.getUserId();
        Chat chat = chatRepository.save(Chat.builder()
                .chatType(Chat.ChatType.GROUP).createdBy(senderId)
                .isPrivate(false).isArchived(false).build());
        chatId = chat.getChatId();
    }

    @Test
    void messagesTableIsRangePartitioned() {
        String relkind = jdbcTemplate.queryForObject(
                "SELECT relkind FROM pg_class WHERE relname = 'messages' AND relnamespace = 'public'::regnamespace",
                String.class);
        assertThat(relkind).isEqualTo("p");
    }

    @Test
    void ensureMessagePartitionsIsIdempotent() {
        Long before = countPartitions();
        jdbcTemplate.execute("SELECT ensure_message_partitions(6)");
        jdbcTemplate.execute("SELECT ensure_message_partitions(6)");
        assertThat(countPartitions()).isEqualTo(before);
    }

    @Test
    void freshMessageLandsInMonthlyPartitionNotDefault() {
        Message message = messageRepository.save(Message.builder()
                .chatId(chatId).senderId(senderId).messageText("hi")
                .messageType(Message.MessageType.TEXT).isDeleted(false).build());

        String partition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM messages WHERE message_id = ?",
                String.class, message.getMessageId());

        assertThat(partition).startsWith("messages_");
        assertThat(partition).isNotEqualTo("messages_default");
    }

    private Long countPartitions() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_inherits WHERE inhparent = 'messages'::regclass",
                Long.class);
    }
}
