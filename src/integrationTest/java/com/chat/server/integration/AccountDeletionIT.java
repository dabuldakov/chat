package com.chat.server.integration;

import com.chat.server.conversation.Attachment;
import com.chat.server.conversation.AttachmentRepository;
import com.chat.server.conversation.AttachmentService;
import com.chat.server.storage.FileStorage;
import com.chat.server.storage.AvatarStorage;
import com.chat.server.identity.JwtUtil;
import com.chat.server.identity.UserSessionService;
import com.chat.server.block.BlockUserRequestDto;
import com.chat.server.block.BlockedUserService;
import com.chat.server.conversation.Chat;
import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Participant;
import com.chat.server.conversation.ParticipantRepository;
import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.contacts.ContactService;
import com.chat.server.exception.NotFoundException;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageRepository;
import com.chat.server.conversation.MessageService;
import com.chat.server.notification.PushNotificationService;
import com.chat.server.account.AccountDeletionService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserAvatarService;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полное удаление аккаунта. Проверяем, что данные исчезают физически, а не
 * просто скрываются: строки считаются нативными запросами в обход
 * {@code @SQLRestriction(is_deleted = false)}, объекты MinIO — попыткой чтения.
 */
@AutoConfigureMockMvc
class AccountDeletionIT extends AbstractIntegrationTest {

    private static final String AVATAR_PREFIX = "/api/avatars/";

    @Autowired private AccountDeletionService deletion;
    @Autowired private UserRepository userRepository;
    @Autowired private UserAvatarService userAvatarService;
    @Autowired private ChatRepository chatRepository;
    @Autowired private ChatService chatService;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageService messageService;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private AttachmentService attachmentService;
    @Autowired private FileStorage fileUploadService;
    @Autowired private AvatarStorage avatarStorage;
    @Autowired private UserSessionService sessionService;
    @Autowired private ContactService contactService;
    @Autowired private BlockedUserService blockedUserService;
    @Autowired private JwtUtil jwt;
    @Autowired private MockMvc mvc;
    @Autowired private DataSource dataSource;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = createUser("alice");
        bob = createUser("bob");
    }

    // ==================== Профиль и связанные таблицы ====================

    @Test
    void removesUserRowPhysically() {
        Long id = alice.getUserId();

        deletion.purgeAccount(id);

        // findById скрыл бы строку из-за @SQLRestriction, поэтому считаем нативно.
        assertThat(countRows("users", "user_id", id)).isZero();
        assertThat(userRepository.findById(id)).isEmpty();
    }

    @Test
    void removesSessionsWithFcmTokens() {
        givenSession(alice, "token-alice", "device-alice", "fcm-alice-token");
        givenSession(bob, "token-bob", "device-bob", "fcm-bob-token");

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("user_sessions", "user_id", alice.getUserId())).isZero();
        // FCM-токен — трансграничные данные, он тоже обязан исчезнуть.
        assertThat(countRows("user_sessions", "fcm_token", "fcm-alice-token")).isZero();
        // Чужие сессии не трогаем.
        assertThat(countRows("user_sessions", "user_id", bob.getUserId())).isEqualTo(1);
        assertThat(countRows("user_sessions", "fcm_token", "fcm-bob-token")).isEqualTo(1);
    }

    @Test
    void removesContactsInBothDirections() {
        contactService.addContact(alice.getUserId(), contactOf(bob));
        contactService.addContact(bob.getUserId(), contactOf(alice));

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("contacts", "user_id", alice.getUserId())).isZero();
        assertThat(countRows("contacts", "contact_user_id", alice.getUserId())).isZero();
        assertThat(countRows("contacts", "user_id", bob.getUserId())).isZero();
    }

    @Test
    void removesBlockedUsersInBothDirections() {
        blockedUserService.blockUser(alice.getUserId(), blockOf(bob));
        blockedUserService.blockUser(bob.getUserId(), blockOf(alice));

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("blocked_users", "user_id", alice.getUserId())).isZero();
        assertThat(countRows("blocked_users", "blocked_user_id", alice.getUserId())).isZero();
    }

    // ==================== Аватар ====================

    @Test
    void removesAvatarObjectFromStorage() throws Exception {
        String key = avatarKey(userAvatarService.upload(alice.getUserId(), png()));
        assertThat(avatarObjectExists(key)).isTrue();

        deletion.purgeAccount(alice.getUserId());

        assertThat(avatarObjectExists(key)).isFalse();
    }

    // ==================== Чаты без других участников ====================

    @Test
    void deletesChatWithMessagesAndAttachmentsWhenUserIsAlone() throws Exception {
        Chat chat = groupChat(alice, List.of());
        Message fromAlice = messageService.sendMessage(chat.getChatId(), alice.getUserId(),
                "секрет", Message.MessageType.TEXT, null, null);
        Attachment attachment = uploadAttachment(alice, chat);
        attachment.setMessageId(fromAlice.getMessageId());
        attachmentRepository.save(attachment);

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("chats", "chat_id", chat.getChatId())).isZero();
        assertThat(countRows("messages", "chat_id", chat.getChatId())).isZero();
        assertThat(countRows("attachments", "chat_id", chat.getChatId())).isZero();
        assertThat(attachmentObjectExists(attachment.getFileUrl())).isFalse();
    }

    @Test
    void keepsPrivateChatForOtherParticipantAndRemovesOnlyOwnMessages() throws Exception {
        Chat chat = privateChat(alice, bob);
        Message fromAlice = messageService.sendMessage(chat.getChatId(), alice.getUserId(),
                "мой пароль", Message.MessageType.TEXT, null, null);
        Message fromBob = messageService.sendMessage(chat.getChatId(), bob.getUserId(),
                "привет", Message.MessageType.TEXT, null, null);
        Attachment aliceAttachment = uploadAttachment(alice, chat);
        aliceAttachment.setMessageId(fromAlice.getMessageId());
        attachmentRepository.save(aliceAttachment);
        Attachment bobAttachment = uploadAttachment(bob, chat);
        bobAttachment.setMessageId(fromBob.getMessageId());
        attachmentRepository.save(bobAttachment);

        deletion.purgeAccount(alice.getUserId());

        // Переписка собеседника — его данные, чат у него остаётся.
        assertThat(chatRepository.findById(chat.getChatId())).isPresent();
        assertThat(messageRepository.findMessagesByChatId(chat.getChatId()))
                .extracting(Message::getMessageText)
                .containsExactly("привет");
        assertThat(countRows("attachments", "attachment_id", aliceAttachment.getAttachmentId()))
                .isZero();
        assertThat(attachmentObjectExists(aliceAttachment.getFileUrl())).isFalse();
        assertThat(countRows("attachments", "attachment_id", bobAttachment.getAttachmentId()))
                .isEqualTo(1);
        assertThat(attachmentObjectExists(bobAttachment.getFileUrl())).isTrue();
    }

    @Test
    void deletesChatWhenOtherParticipantIsSoftDeleted() {
        Chat chat = privateChat(alice, bob);
        // У мягко удалённого участника нет активных прав на данные, поэтому
        // чат остаётся удаляемым целиком.
        bob.setIsDeleted(true);
        bob.setStatus(User.UserStatus.INACTIVE);
        userRepository.save(bob);

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("chats", "chat_id", chat.getChatId())).isZero();
    }

    // ==================== Групповые чаты ====================

    @Test
    void keepsSharedChatAndRemovesOnlyOwnData() {
        Chat chat = groupChat(alice, List.of(bob, createUser("carol")));
        messageService.sendMessage(chat.getChatId(), alice.getUserId(),
                "моё сообщение", Message.MessageType.TEXT, null, null);
        messageService.sendMessage(chat.getChatId(), bob.getUserId(),
                "сообщение бота", Message.MessageType.TEXT, null, null);

        deletion.purgeAccount(alice.getUserId());

        assertThat(chatRepository.findById(chat.getChatId())).isPresent();
        assertThat(countRows("participants", "user_id", alice.getUserId())).isZero();
        assertThat(countRows("participants", "chat_id", chat.getChatId())).isEqualTo(2);
        // Сообщение второго участника и сам чат для него остаются.
        assertThat(messageRepository.findMessagesByChatId(chat.getChatId()))
                .extracting(Message::getMessageText)
                .containsExactly("сообщение бота");
    }

    @Test
    void transfersGroupOwnershipWhenCreatorIsDeleted() {
        Chat chat = groupChat(alice, List.of(bob));

        deletion.purgeAccount(alice.getUserId());

        Participant bobParticipant = participantRepository
                .findByChatIdAndUserId(chat.getChatId(), bob.getUserId()).orElseThrow();
        assertThat(bobParticipant.getRole()).isEqualTo(Participant.ParticipantRole.OWNER);
        // Владение в самом чате тоже: иначе deleteChat и removeParticipant
        // для нового владельца были бы недоступны.
        assertThat(chatRepository.findById(chat.getChatId()).orElseThrow().getCreatedBy())
                .isEqualTo(bob.getUserId());
    }

    @Test
    void refreshesLastMessagePreviewForOtherParticipants() {
        Chat chat = groupChat(alice, List.of(bob));
        messageService.sendMessage(chat.getChatId(), bob.getUserId(),
                "привет", Message.MessageType.TEXT, null, null);
        Message lastFromAlice = messageService.sendMessage(chat.getChatId(), alice.getUserId(),
                "пока", Message.MessageType.TEXT, null, null);
        assertThat(chatRepository.findById(chat.getChatId()).orElseThrow().getLastMessageId())
                .isEqualTo(lastFromAlice.getMessageId());

        deletion.purgeAccount(alice.getUserId());

        Chat kept = chatRepository.findById(chat.getChatId()).orElseThrow();
        // Превью в списке чатов у другого участника не должно показывать
        // удалённое сообщение.
        assertThat(kept.getLastMessageId()).isNotEqualTo(lastFromAlice.getMessageId());
        assertThat(kept.getLastMessageText()).isEqualTo("привет");
        assertThat(kept.getMessageCount()).isEqualTo(1);
    }

    @Test
    void keepsOtherUsersAttachmentsInSharedChat() throws Exception {
        Chat chat = groupChat(alice, List.of(bob));
        Message fromBob = messageService.sendMessage(chat.getChatId(), bob.getUserId(),
                "фото", Message.MessageType.IMAGE, null, null);
        Attachment bobAttachment = uploadAttachment(bob, chat);
        bobAttachment.setMessageId(fromBob.getMessageId());
        attachmentRepository.save(bobAttachment);
        Message fromAlice = messageService.sendMessage(chat.getChatId(), alice.getUserId(),
                "мой файл", Message.MessageType.FILE, null, null);
        Attachment aliceAttachment = uploadAttachment(alice, chat);
        aliceAttachment.setMessageId(fromAlice.getMessageId());
        attachmentRepository.save(aliceAttachment);

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("attachments", "attachment_id", bobAttachment.getAttachmentId()))
                .isEqualTo(1);
        assertThat(countRows("attachments", "attachment_id", aliceAttachment.getAttachmentId()))
                .isZero();
        assertThat(attachmentObjectExists(bobAttachment.getFileUrl())).isTrue();
        assertThat(attachmentObjectExists(aliceAttachment.getFileUrl())).isFalse();
    }

    @Test
    void removesUnattachedUploads() throws Exception {
        Chat chat = groupChat(alice, List.of(bob));
        Attachment orphan = uploadAttachment(alice, chat);
        assertThat(orphan.getMessageId()).isNull();

        deletion.purgeAccount(alice.getUserId());

        assertThat(countRows("attachments", "attachment_id", orphan.getAttachmentId())).isZero();
        assertThat(attachmentObjectExists(orphan.getFileUrl())).isFalse();
    }

    // ==================== HTTP ====================

    @Test
    void deleteAccountEndpointReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/users/me").header("Authorization", tokenOf(alice)))
                .andExpect(status().isNoContent());

        assertThat(countRows("users", "user_id", alice.getUserId())).isZero();
    }

    @Test
    void deleteAccountEndpointRejectsAnonymous() throws Exception {
        mvc.perform(delete("/api/users/me"))
                .andExpect(status().isUnauthorized());

        assertThat(countRows("users", "user_id", alice.getUserId())).isEqualTo(1);
    }

    @Test
    void deleteAccountEndpointRemovesOnlyOwnAccount() throws Exception {
        mvc.perform(delete("/api/users/me").header("Authorization", tokenOf(alice)))
                .andExpect(status().isNoContent());

        assertThat(countRows("users", "user_id", bob.getUserId())).isEqualTo(1);
    }

    @Test
    void failsWhenAccountAlreadyGone() {
        Long id = alice.getUserId();
        deletion.purgeAccount(id);

        assertThatThrownBy(() -> deletion.purgeAccount(id))
                .isInstanceOf(NotFoundException.class);
    }

    // ==================== Вспомогательное ====================

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    private long countRows(String table, String column, Object value) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM " + table + " WHERE " + column + " = ?")) {
            statement.setObject(1, value);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Chat privateChat(User a, User b) {        return chatRepository.findByChatUuid(
                chatService.createPrivateChat(a.getUserId(), b.getUserUuid()).getChatUuid())
                .orElseThrow();
    }

    private Chat groupChat(User creator, List<User> members) {
        return chatRepository.findByChatUuid(chatService.createGroupChat("группа",
                        creator.getUserId(),
                        members.stream().map(User::getUserUuid).toList())
                .getChatUuid())
                .orElseThrow();
    }

    private AddContactRequestDto contactOf(User user) {
        AddContactRequestDto dto = new AddContactRequestDto();
        dto.setContactUserUuid(user.getUserUuid());
        return dto;
    }

    private BlockUserRequestDto blockOf(User user) {
        BlockUserRequestDto dto = new BlockUserRequestDto();
        dto.setBlockUserUuid(user.getUserUuid());
        return dto;
    }

    private String tokenOf(User user) {
        return "Bearer " + jwt.generateToken(user.getUserUuid(), user.getUsername());
    }

    /**
     * registerFcmToken только обновляет существующую сессию устройства, поэтому
     * сессию нужно создать явно — иначе токен негде хранить.
     */
    private void givenSession(User user, String token, String deviceId, String fcmToken) {
        sessionService.createSession(user.getUserId(), token, token + "-refresh",
                deviceId, "Pixel", "ANDROID", "127.0.0.1", "junit");
        sessionService.registerFcmToken(user.getUserId(), deviceId, fcmToken);
    }

    private Attachment uploadAttachment(User user, Chat chat) throws Exception {
        return attachmentService.uploadAttachment(png(), chat.getChatId(), user.getUserId());
    }

    private MockMultipartFile png() throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "file.png", "image/png", out.toByteArray());
    }

    private String avatarKey(String url) {
        return url.substring(AVATAR_PREFIX.length());
    }

    private boolean avatarObjectExists(String key) {
        try {
            avatarStorage.get(key);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean attachmentObjectExists(String key) {
        try {
            return fileUploadService.load(key).exists();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
