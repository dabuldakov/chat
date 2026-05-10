package com.chat.server.repository;

import com.chat.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.username LIKE %:query% OR u.email LIKE %:query%")
    List<User> searchByUsernameOrEmail(@Param("query") String query);

    @Query("SELECT u FROM User u WHERE u.userId IN :userIds")
    List<User> findAllByIds(@Param("userIds") List<UUID> userIds);
}