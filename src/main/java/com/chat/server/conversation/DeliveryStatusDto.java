package com.chat.server.conversation;

public enum DeliveryStatusDto {
    SENT,      // Отправлено на сервер
    DELIVERED, // Доставлено на устройство
    READ,      // Прочитано
    FAILED     // Ошибка доставки
}
