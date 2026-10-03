package com.chat.server.account;

import com.chat.server.conversation.Attachment;
import com.chat.server.conversation.AttachmentRepository;
import com.chat.server.storage.FileUploadService;
import com.chat.server.conversation.Chat;
import com.chat.server.conversation.ChatAvatarService;
import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.Participant;
import com.chat.server.conversation.ParticipantRepository;
import com.chat.server.exception.NotFoundException;
import com.chat.server.identity.User;
import com.chat.server.identity.UserAvatarService;
import com.chat.server.identity.UserRepository;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Полное удаление аккаунта вместе с персональными данными (ст. 12 ФЗ-152).
 * <p>
 * Раньше {@code DELETE /api/users/me} только ставил {@code is_deleted = true}:
 * аккаунт скрывался, но в базе оставались сообщения, вложения, контакты,
 * аватары и FCM-токены. Здесь данные уничтожаются по-настоящему.
 * <p>
 * Что удаляется:
 * <ul>
 *   <li>строка пользователя — каскадом уходят сессии (вместе с FCM-токенами),
 *       контакты (в обе стороны) и заблокированные пользователи;</li>
 *   <li>чаты, где других активных участников нет, вместе с сообщениями,
 *       вложениями и аватаром чата;</li>
 *   <li>в чатах с другими участниками — сообщения пользователя, его вложения
 *       и строка участника; чат остаётся, владение передаётся другому
 *       участнику;</li>
 *   <li>аватар пользователя и объекты в MinIO.</li>
 * </ul>
 * Объекты MinIO и кэши чистятся после коммита транзакции: если транзакция
 * откатится, файлы останутся в базе, а не пропадут без записи о них.
 * <p>
 * Поля {@code messages.reply_to_message_id} и {@code forwarded_from_*} после
 * удаления ссылаются на несуществующие id. Это безымянные ссылки, и код их не
 * разыменовывает при отдаче сообщений, поэтому точечная правка не делается.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final UserAvatarService userAvatars;
    private final ChatRepository chatRepository;
    private final ChatAvatarService chatAvatars;
    private final ParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileUploadService fileUploadService;
    private final CacheManager cacheManager;

    @Transactional
    public void purgeAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        String userAvatarKey = userAvatars.storedKey(user.getAvatarUrl());
        List<String> chatAvatarKeys = new ArrayList<>();
        List<String> fileKeys = new ArrayList<>();

        // Чаты делим на два типа: без других активных участников чат удаляется
        // целиком, иначе остаётся — иначе у других людей исчезли бы их
        // собственные чаты.
        List<Long> keptChatIds = new ArrayList<>();
        for (Long chatId : participantRepository.findChatIdsByUserId(userId)) {
            List<Participant> members = participantRepository.findAllByChatId(chatId);
            List<Long> otherIds = members.stream()
                    .map(Participant::getUserId)
                    .filter(id -> !Objects.equals(id, userId))
                    .toList();
            // findAllById не видит мягко удалённых пользователей (@SQLRestriction),
            // поэтому чат с единственным активным участником считаем опустевшим.
            if (userRepository.findAllById(otherIds).isEmpty()) {
                deleteChat(chatId, chatAvatarKeys, fileKeys);
            } else {
                keptChatIds.add(chatId);
            }
        }

        // Сообщения пользователя в оставшихся чатах: их удалил бы и каскад по
        // messages.sender_id, но вложения он не трогает (FK attachments ->
        // messages нет), поэтому объекты в MinIO осиротели бы.
        List<Long> removedMessageIds = messageRepository
                .findAllMessageIdsBySenderIdAndChatIdIn(userId, keptChatIds);
        for (Long messageId : removedMessageIds) {
            collectFileKeys(attachmentRepository.findByMessageId(messageId), fileKeys);
        }
        for (Attachment attachment : attachmentRepository.findByUploaderIdAndMessageIdIsNull(userId)) {
            collectFileKeys(List.of(attachment), fileKeys);
        }
        // Вложения, прикреплённые к чужим сообщениям, не трогаем: такой файл
        // уже является частью чужого сообщения.
        if (!removedMessageIds.isEmpty()) {
            messageRepository.hardDeleteBySenderIdAndChatIdIn(userId, keptChatIds);
            attachmentRepository.deleteByMessageIds(removedMessageIds);
        }
        attachmentRepository.deleteByUploaderIdAndMessageIdIsNull(userId);

        for (Long chatId : keptChatIds) {
            leaveChat(chatId, userId, removedMessageIds);
        }

        userRepository.delete(user);
        userRepository.flush();

        scheduleCleanup(user.getUserUuid(), userAvatarKey,
                List.copyOf(chatAvatarKeys), List.copyOf(fileKeys), removedMessageIds.size());
    }

    /**
     * Удаляет чат целиком: вложения, сообщения, участников, сам чат.
     */
    private void deleteChat(Long chatId, List<String> chatAvatarKeys, List<String> fileKeys) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (chat == null) {
            return;
        }
        addIfPresent(chatAvatarKeys, chatAvatars.storedKey(chat.getAvatarUrl()));
        for (Attachment attachment : attachmentRepository.findByChatId(chatId)) {
            collectFileKeys(List.of(attachment), fileKeys);
        }
        attachmentRepository.deleteByChatId(chatId);
        messageRepository.hardDeleteAllMessagesInChat(chatId);
        participantRepository.deleteAll(participantRepository.findAllByChatId(chatId));
        chatRepository.delete(chat);
    }

    /**
     * Убирает пользователя из чата, не удаляя сам чат: снимает строку участника,
     * передаёт владение (иначе группа остаётся без владельца и ею нельзя
     * управлять) и пересчитывает превью последнего сообщения.
     */
    private void leaveChat(Long chatId, Long userId, List<Long> removedMessageIds) {
        participantRepository.deleteByChatIdAndUserId(chatId, userId);
        chatRepository.findById(chatId).ifPresent(chat -> {
            if (Objects.equals(chat.getCreatedBy(), userId)) {
                transferOwnership(chatId);
            }
            refreshPreview(chat, removedMessageIds);
        });
    }

    private void transferOwnership(Long chatId) {
        List<Participant> remaining = participantRepository.findAllByChatId(chatId);
        // Владельцем назначаем действующего участника, а не мягко удалённого.
        List<Participant> active = remaining.stream()
                .filter(p -> userRepository.existsById(p.getUserId()))
                .toList();
        List<Participant> candidates = active.isEmpty() ? remaining : active;
        candidates.stream()
                .filter(p -> p.getRole() != null && p.getRole() != Participant.ParticipantRole.MEMBER)
                .min(Comparator.comparing(Participant::getJoinedAt))
                .or(() -> candidates.stream().min(Comparator.comparing(Participant::getJoinedAt)))
                .ifPresent(newOwner -> {
                    chatRepository.findById(chatId).ifPresent(chat -> chat.setCreatedBy(newOwner.getUserId()));
                    newOwner.setRole(Participant.ParticipantRole.OWNER);
                    participantRepository.save(newOwner);
                    log.info("Chat {} ownership transferred to user {}", chatId, newOwner.getUserId());
                });
    }

    /**
     * Превью и счётчик сообщений в списке чатов у других участников не должны
     * продолжать показывать удалённое сообщение.
     */
    private void refreshPreview(Chat chat, List<Long> removedMessageIds) {
        if (removedMessageIds.isEmpty()) {
            return;
        }
        if (removedMessageIds.contains(chat.getLastMessageId())) {
            messageRepository.findLastMessageInChat(chat.getChatId())
                    .ifPresentOrElse(last -> applyPreview(chat, last), () -> applyPreview(chat, null));
        }
        chat.setMessageCount(messageRepository.countAllInChat(chat.getChatId()));
    }

    private void applyPreview(Chat chat, Message last) {
        chat.setLastMessageId(last == null ? null : last.getMessageId());
        chat.setLastMessageText(last == null ? null : last.getMessageText());
        chat.setLastMessageSenderId(last == null ? null : last.getSenderId());
    }

    private void collectFileKeys(List<Attachment> attachments, List<String> fileKeys) {
        for (Attachment attachment : attachments) {
            addIfPresent(fileKeys, attachment.getFileUrl());
            addIfPresent(fileKeys, attachment.getThumbnailUrl());
        }
    }

    private void addIfPresent(List<String> target, String key) {
        if (key != null && !key.isBlank()) {
            target.add(key);
        }
    }

    private void scheduleCleanup(UUID userUuid, String userAvatarKey,
                                List<String> chatAvatarKeys, List<String> fileKeys, int messageCount) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup(userUuid, userAvatarKey, chatAvatarKeys, fileKeys, messageCount);
                }
            });
        } else {
            cleanup(userUuid, userAvatarKey, chatAvatarKeys, fileKeys, messageCount);
        }
    }

    private void cleanup(UUID userUuid, String userAvatarKey,
                         List<String> chatAvatarKeys, List<String> fileKeys, int messageCount) {
        userAvatars.deleteStoredObject(userAvatarKey);
        chatAvatarKeys.forEach(chatAvatars::deleteStoredObject);
        fileKeys.forEach(this::deleteStoredFile);
        clearCaches();
        // В лог только идентификаторы: он не должен копить персональные данные.
        log.info("Account purged: uuid={}, messages={}, files={}", userUuid, messageCount, fileKeys.size());
    }

    private void deleteStoredFile(String key) {
        try {
            fileUploadService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to delete stored file {}", key, e);
        }
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
