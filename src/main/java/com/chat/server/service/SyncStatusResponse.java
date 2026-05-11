package com.chat.server.service;

import java.time.LocalDateTime;

public record SyncStatusResponse(
        LocalDateTime lastFullSync,
        LocalDateTime lastMessagesSync,
        long totalChats,
        long totalMessages,
        long pendingUploads
) {
}
