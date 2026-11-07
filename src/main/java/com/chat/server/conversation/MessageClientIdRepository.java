package com.chat.server.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageClientIdRepository extends JpaRepository<MessageClientId, UUID> {

    Optional<MessageClientId> findByClientMessageId(UUID clientMessageId);
}
