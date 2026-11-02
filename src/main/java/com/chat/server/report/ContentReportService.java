package com.chat.server.report;

import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageRepository;
import com.chat.server.conversation.ParticipantRepository;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.BadRequestException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Приём жалоб на контент. Жалоба идемпотентна: повторная жалоба того же
 * пользователя на тот же объект возвращает уже созданную запись, а не ошибку.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReportService {

    private static final int PREVIEW_LIMIT = 500;

    private final ContentReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;
    private final ChatService chatService;
    private final UserService userService;

    @Transactional
    public ContentReport createReport(Long reporterId, CreateReportRequestDto request) {
        UUID contentUuid = request.getContentUuid();

        var existing = reportRepository.findByReporterIdAndContentTypeAndContentUuid(
                reporterId, request.getContentType(), contentUuid);
        if (existing.isPresent()) {
            return existing.get();
        }

        Long targetUserId = null;
        String preview = null;

        switch (request.getContentType()) {
            case MESSAGE -> {
                Message message = messageRepository.findByMessageUuid(contentUuid)
                        .orElseThrow(() -> new NotFoundException("Message not found"));
                if (!participantRepository.existsByChatIdAndUserId(message.getChatId(), reporterId)) {
                    throw new AccessDeniedException("Cannot report a message from a chat you do not participate in");
                }
                if (message.getSenderId().equals(reporterId)) {
                    throw new BadRequestException("Cannot report your own message");
                }
                targetUserId = message.getSenderId();
                preview = truncate(message.getMessageText());
            }
            case USER -> {
                User user = userService.getUserByUuid(contentUuid);
                if (user.getUserId().equals(reporterId)) {
                    throw new BadRequestException("Cannot report yourself");
                }
                targetUserId = user.getUserId();
            }
            case CHAT -> {
                Long chatId = chatService.getChatIdByUuid(contentUuid);
                if (!participantRepository.existsByChatIdAndUserId(chatId, reporterId)) {
                    throw new AccessDeniedException("Cannot report a chat you do not participate in");
                }
            }
        }

        ContentReport report = ContentReport.builder()
                .reporterId(reporterId)
                .contentType(request.getContentType())
                .contentUuid(contentUuid)
                .targetUserId(targetUserId)
                .reason(request.getReason())
                .comment(trimToNull(request.getComment()))
                .contentPreview(preview)
                .status(ContentReport.Status.NEW)
                .build();

        try {
            ContentReport saved = reportRepository.save(report);
            log.warn("Content report created: type={}, contentUuid={}, reason={}, reporter={}, target={}",
                    saved.getContentType(), saved.getContentUuid(), saved.getReason(),
                    reporterId, targetUserId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Параллельный дубль: возвращаем уже сохранённую жалобу.
            return reportRepository.findByReporterIdAndContentTypeAndContentUuid(
                            reporterId, request.getContentType(), contentUuid)
                    .orElseThrow(() -> e);
        }
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() <= PREVIEW_LIMIT ? text : text.substring(0, PREVIEW_LIMIT);
    }

    private static String trimToNull(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
