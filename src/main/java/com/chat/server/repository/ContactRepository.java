package com.chat.server.repository;

import com.chat.server.entity.Contact;
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
public interface ContactRepository extends JpaRepository<Contact, Long> {

    // ==================== Поиск по UUID ====================

    Optional<Contact> findByContactUuid(UUID contactUuid);

    // ==================== Поиск контактов пользователя ====================

    List<Contact> findByUserId(Long userId);

    Page<Contact> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT c FROM Contact c WHERE c.userId = :userId ORDER BY c.contactName ASC")
    List<Contact> findByUserIdOrderByName(@Param("userId") Long userId);

    // ==================== Поиск конкретного контакта ====================

    Optional<Contact> findByUserIdAndContactUserId(Long userId, Long contactUserId);

    @Query("SELECT c FROM Contact c WHERE c.userId = :userId AND c.contactUserId IN :contactUserIds")
    List<Contact> findByUserIdAndContactUserIds(@Param("userId") Long userId,
                                                @Param("contactUserIds") List<Long> contactUserIds);

    // ==================== Проверка существования ====================

    boolean existsByUserIdAndContactUserId(Long userId, Long contactUserId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Contact c WHERE c.userId = :userId AND c.contactUserId = :contactUserId")
    boolean isContactExists(@Param("userId") Long userId, @Param("contactUserId") Long contactUserId);

    // ==================== Подсчет контактов ====================

    long countByUserId(Long userId);

    // ==================== Удаление ====================

    @Modifying
    void deleteByUserIdAndContactUserId(Long userId, Long contactUserId);

    @Modifying
    void deleteByUserId(Long userId);

    // ==================== Поиск с фильтрацией ====================

    @Query("""
        SELECT c FROM Contact c 
        WHERE c.userId = :userId 
        AND (LOWER(c.contactName) LIKE LOWER(CONCAT('%', :search, '%')) 
             OR EXISTS (SELECT u FROM User u WHERE u.userId = c.contactUserId AND LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))))
        ORDER BY c.contactName ASC
    """)
    Page<Contact> searchContacts(@Param("userId") Long userId,
                                 @Param("search") String search,
                                 Pageable pageable);

    // ==================== Получение ID контактов ====================

    @Query("SELECT c.contactUserId FROM Contact c WHERE c.userId = :userId")
    List<Long> findContactUserIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT c.contactUserId FROM Contact c WHERE c.userId = :userId AND c.contactUserId IN :userIds")
    List<Long> findExistingContactIds(@Param("userId") Long userId,
                                      @Param("userIds") List<Long> userIds);
}