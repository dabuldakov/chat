package com.chat.server.identity;

import com.chat.server.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Устройство пользователя и его FCM-токен для пуш-уведомлений.
 *
 * <p>Раньше токены лежали в {@code user_sessions} — таблице парольных сессий.
 * Вход теперь только через единый IdP (auth-service), сессий у chat больше нет,
 * поэтому под push заведена отдельная таблица. Одна строка на устройство.
 */
@Getter
@Setter
@ToString(callSuper = true)
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "device_tokens",
        indexes = {
                @Index(name = "idx_device_tokens_user_id", columnList = "user_id"),
                @Index(name = "uk_device_tokens_fcm_token", columnList = "fcm_token", unique = true)
        })
public class DeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Идентификатор устройства из приложения. Может быть null. */
    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "fcm_token", nullable = false, length = 500)
    private String fcmToken;
}
