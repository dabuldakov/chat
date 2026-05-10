package com.chat.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final CacheManager cacheManager;

    public void evictUserCache(Long userId) {
        cacheManager.getCache("users").evict(userId);
        log.debug("User cache evicted for: {}", userId);
    }

    public void evictChatCache(Long chatId) {
        cacheManager.getCache("chats").evict(chatId);
        log.debug("Chat cache evicted for: {}", chatId);
    }

    public void evictAllCaches() {
        cacheManager.getCacheNames().stream()
                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());
        log.info("All caches cleared");
    }
}