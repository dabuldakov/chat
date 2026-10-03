package com.chat.server.contacts;

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

import com.chat.server.identity.User;
@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByContactUuid(UUID contactUuid);

    Page<Contact> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT c FROM Contact c WHERE c.userId = :userId ORDER BY c.contactName ASC")
    List<Contact> findByUserIdOrderByName(@Param("userId") Long userId);

    boolean existsByUserIdAndContactUserId(Long userId, Long contactUserId);

    @Modifying
    void deleteByUserIdAndContactUserId(Long userId, Long contactUserId);

    @Modifying
    void deleteByUserId(Long userId);

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

    @Query("SELECT c.contactUserId FROM Contact c WHERE c.userId = :userId")
    List<Long> findContactUserIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT c.contactUserId FROM Contact c WHERE c.userId = :userId AND c.contactUserId IN :userIds")
    List<Long> findExistingContactIds(@Param("userId") Long userId,
                                      @Param("userIds") List<Long> userIds);
}
