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
        int maxRetries = 5;
        int delayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Clearing all Redis caches on startup... (attempt {}/{})", attempt, maxRetries);
                redisConnectionFactory.getConnection().serverCommands().flushDb();
                log.info("Redis cache cleared successfully on startup.");
                return;
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    delayMs *= 2; // exponential backoff
                } else {
                    log.error("Failed to clear Redis cache after {} attempts", maxRetries);
                }
            }
        }
    }
}