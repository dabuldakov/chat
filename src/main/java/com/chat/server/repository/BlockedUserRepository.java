package com.chat.server.repository;

import com.chat.server.entity.BlockedUser;
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
public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    // ==================== Поиск по UUID ====================

    Optional<BlockedUser> findByBlockUuid(UUID blockUuid);

    // ==================== Поиск блокировок пользователя ====================

    List<BlockedUser> findByUserId(Long userId);

    Page<BlockedUser> findByUserId(Long userId, Pageable pageable);

    // ==================== Поиск конкретной блокировки ====================

    Optional<BlockedUser> findByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    @Query("SELECT b FROM BlockedUser b WHERE b.userId = :userId AND b.blockedUserId IN :blockedUserIds")
    List<BlockedUser> findByUserIdAndBlockedUserIds(@Param("userId") Long userId,
                                                    @Param("blockedUserIds") List<Long> blockedUserIds);

    // ==================== Проверка блокировки ====================

    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BlockedUser b WHERE b.userId = :userId AND b.blockedUserId = :blockedUserId")
    boolean isBlocked(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);

    // ==================== Проверка взаимной блокировки ====================

    @Query("""
        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END 
        FROM BlockedUser b 
        WHERE (b.userId = :userId AND b.blockedUserId = :otherUserId) 
           OR (b.userId = :otherUserId AND b.blockedUserId = :userId)
    """)
    boolean isMutualBlock(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    // ==================== Подсчет ====================

    long countByUserId(Long userId);

    // ==================== Удаление ====================

    @Modifying
    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    @Modifying
    void deleteByUserId(Long userId);

    // ==================== Получение ID заблокированных ====================

    @Query("SELECT b.blockedUserId FROM BlockedUser b WHERE b.userId = :userId")
    List<Long> findBlockedUserIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT b.userId FROM BlockedUser b WHERE b.blockedUserId = :blockedUserId")
    List<Long> findUserIdsWhoBlocked(@Param("blockedUserId") Long blockedUserId);

    // ==================== Поиск с фильтрацией ====================

    @Query("""
        SELECT b FROM BlockedUser b 
        WHERE b.userId = :userId 
        AND EXISTS (SELECT u FROM User u WHERE u.userId = b.blockedUserId AND LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY b.createdAt DESC
    """)
    Page<BlockedUser> searchBlockedUsers(@Param("userId") Long userId,
                                         @Param("search") String search,
                                         Pageable pageable);

    // ==================== Блокировки по времени ====================

    @Query("SELECT b FROM BlockedUser b WHERE b.createdAt > :since")
    List<BlockedUser> findBlockedSince(@Param("since") java.time.LocalDateTime since);
}