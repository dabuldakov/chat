package com.chat.server.dto.response;

public enum DeliveryStatusDto {
    SENT,      // Отправлено на сервер
    DELIVERED, // Доставлено на устройство
    READ,      // Прочитано
    FAILED     // Ошибка доставки
}
