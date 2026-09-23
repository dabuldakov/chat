package com.chat.server.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    // UserRepository.java - добавить эти методы
    Optional<User> findByResetToken(String resetToken);

    Optional<User> findByEmailVerificationToken(String emailVerificationToken);

    @Query("UPDATE User u SET u.isOnline = true, u.lastSeenAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    @Modifying
    void setUserOnline(@Param("userId") Long userId);

    @Query("UPDATE User u SET u.isOnline = false, u.lastSeenAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    @Modifying
    void setUserOffline(@Param("userId") Long userId);
}