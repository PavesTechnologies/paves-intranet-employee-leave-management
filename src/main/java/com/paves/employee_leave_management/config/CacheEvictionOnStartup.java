package com.paves.employee_leave_management.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionOnStartup {

    private final RedisConnectionFactory redisConnectionFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void evictAllCachesOnStartup() {
        try {
            log.info("Clearing all Redis caches on startup...");
            redisConnectionFactory.getConnection().serverCommands().flushDb();
            log.info("Redis cache cleared successfully on startup.");
        } catch (Exception e) {
            log.error("Failed to clear Redis cache on startup: {}", e.getMessage());
        }
    }
}