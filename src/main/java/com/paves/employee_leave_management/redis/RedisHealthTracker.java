package com.paves.employee_leave_management.redis;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class RedisHealthTracker {
    private final RedisConnectionFactory connectionFactory;
    private final AtomicBoolean redisUp = new AtomicBoolean(false);

    public RedisHealthTracker(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        // Check immediately on startup
        checkRedis();
    }

    @Scheduled(fixedDelay = 30000)
    public void checkRedis() {
        try {
            RedisConnection conn = connectionFactory.getConnection();
            conn.ping();
            conn.close();
            if (!redisUp.get()) {
                log.info("Redis is back online — resuming Redis cache");
            }
            redisUp.set(true);
        } catch (Exception e) {
            if (redisUp.get()) {
                log.warn("Redis went down — switching to in-memory cache fallback");
            }
            redisUp.set(false);
        }
    }

    public boolean isRedisUp() {
        return redisUp.get();
    }
}
