package com.chat.server.conversation;

import lombok.Builder;
import lombok.Data;

/** Сводка реакций на сообщение: эмодзи, число и поставил ли текущий пользователь. */
@Data
@Builder
public class ReactionDto {
    private String emoji;
    private int count;
    private boolean mine;
}
