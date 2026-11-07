package com.chat.server.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceTokenRepository extends JpaRepository<ServiceToken, Long> {

    Optional<ServiceToken> findByTokenHash(String tokenHash);
}
