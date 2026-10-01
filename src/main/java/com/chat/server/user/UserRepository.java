package com.chat.server.user;

import com.chat.server.presence.PresenceProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUserUuid(UUID userUuid);

    @Query("SELECT u.userId FROM User u WHERE u.userUuid = :userUuid")
    Optional<Long> findUserIdByUserUuid(@Param("userUuid") UUID userUuid);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Полнотекстовый поиск по username/first_name/last_name с префиксным
     * совпадением (как поиск "на лету" в клиенте). Использует существующий
     * GIN-индекс idx_users_search_gin (V2) — выражение индекса должно
     * совпадать с выражением в запросе.
     */
    @Query(value = """
        SELECT u.* FROM users u
        WHERE u.is_deleted = false
          AND to_tsvector('russian',
                 COALESCE(u.username, '') || ' ' || COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, ''))
              @@ (SELECT to_tsquery('russian',
                        string_agg(regexp_replace(trim(word), '[^\\w]', '', 'g') || ':*', ' & '))
                  FROM regexp_split_to_table(:query, '\\s+') AS word)
        ORDER BY u.username ASC
        """, nativeQuery = true)
    Page<User> searchByUsernameOrEmail(@Param("query") String query, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.userId IN :userIds")
    List<User> findAllByUserIdList(@Param("userIds") List<Long> userIds);

    @Query("SELECT u FROM User u WHERE u.isOnline = true AND u.isDeleted = false")
    List<User> findOnlineUsers();

    @Query("SELECT u FROM User u WHERE u.lastSeenAt > :since AND u.isDeleted = false")
    List<User> findActiveSince(@Param("since") java.time.LocalDateTime since);

    Optional<User> findByResetToken(String resetToken);

    Optional<User> findByEmailVerificationToken(String emailVerificationToken);

    // ==================== Presence ====================

    /**
     * Heartbeat одним UPDATE: обновляет last_seen_at и поднимает флаг.
     * Пишем запросом, а не через save(), чтобы не читать сущность и не
     * трогать кэш пользователей на каждом heartbeat.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE User u SET u.lastSeenAt = :now, u.isOnline = true
        WHERE u.userId = :userId AND u.isDeleted = false
        """)
    int touchPresence(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Снимает флаг «онлайн» (разлогин). last_seen_at не трогаем — это время
     * последней активности, по нему клиент показывает «был в сети».
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.isOnline = false WHERE u.userId = :userId")
    int clearPresence(@Param("userId") Long userId);

    /**
     * PresenceSweeper: снимает флаг у пользователей, heartbeat которых
     * протух. Использует частичный индекс idx_users_online_last_seen (V10) —
     * условие в индексе совпадает с условием запроса.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE User u SET u.isOnline = false
        WHERE u.isOnline = true
          AND (u.lastSeenAt IS NULL OR u.lastSeenAt < :threshold)
        """)
    int expireStalePresence(@Param("threshold") LocalDateTime threshold);

    /** Присутствие по идентификаторам — мимо кэша пользователей. */
    @Query("""
        SELECT u.userId AS userId, u.userUuid AS userUuid, u.lastSeenAt AS lastSeenAt
        FROM User u
        WHERE u.userId IN :userIds AND u.isDeleted = false
        """)
    List<PresenceProjection> findPresenceByUserIds(@Param("userIds") Collection<Long> userIds);

    /** Присутствие по UUID — мимо кэша пользователей. */
    @Query("""
        SELECT u.userId AS userId, u.userUuid AS userUuid, u.lastSeenAt AS lastSeenAt
        FROM User u
        WHERE u.userUuid IN :userUuids AND u.isDeleted = false
        """)
    List<PresenceProjection> findPresenceByUserUuids(@Param("userUuids") Collection<UUID> userUuids);
}
