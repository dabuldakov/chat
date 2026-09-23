package com.chat.server.sync;

import com.chat.server.chat.Chat;
import com.chat.server.message.Message;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SyncResponseDto {

    private LocalDateTime syncTime;
    private List<Long> chatIds;
    private List<Chat> updatedChats;
    private Map<Long, List<Message>> newMessages;
    private Map<Long, List<Long>> newParticipants;
    private Map<Long, List<Long>> removedParticipants;
}