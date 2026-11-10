package com.chat.server.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, Long> {
    boolean existsByEventId(UUID eventId);
}
