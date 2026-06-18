//package com.paves.employee_leave_management.redis;
//
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.redis.connection.RedisConnection;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.concurrent.atomic.AtomicBoolean;
//
//@Component
//@Slf4j
//public class RedisHealthTracker {
//    private final RedisConnectionFactory connectionFactory;
//    private final AtomicBoolean redisUp = new AtomicBoolean(false);
//
//    public RedisHealthTracker(RedisConnectionFactory connectionFactory) {
//        this.connectionFactory = connectionFactory;
//        // Check immediately on startup
//        checkRedis();
//    }
//
//    @Scheduled(fixedDelay = 30000)
//    public void checkRedis() {
//        try {
//            RedisConnection conn = connectionFactory.getConnection();
//            conn.ping();
//            conn.close();
//            if (!redisUp.get()) {
//                log.info("Redis is back online — resuming Redis cache");
//            }
//            redisUp.set(true);
//        } catch (Exception e) {
//            if (redisUp.get()) {
//                log.warn("Redis went down — switching to in-memory cache fallback");
//            }
//            redisUp.set(false);
//        }
//    }
//
//    public boolean isRedisUp() {
//        return redisUp.get();
//    }
//}

package com.paves.employee_leave_management.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class RedisHealthTracker {

    private final RedisConnectionFactory connectionFactory;
    private final AtomicBoolean redisUp = new AtomicBoolean(false);

    // Timestamp of the last completed health probe (success or failure).
    // Used by SafeRedisCache to decide whether to trigger an immediate
    // re-probe or trust the cached status — prevents a thundering herd of
    // synchronous Redis pings when Redis is down and every cache miss would
    // otherwise trigger one.
    private final AtomicLong lastCheckedAt = new AtomicLong(0);

    // Minimum gap between on-demand probes triggered by SafeRedisCache.
    // The scheduled probe still runs every 30s regardless.
    private static final long ON_DEMAND_PROBE_COOLDOWN_MS = 10_000;

    public RedisHealthTracker(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        // Do NOT call checkRedis() here.
        // Calling it in the constructor means a 5s socket-timeout hang if
        // Redis is down at startup — before Spring has finished initialising
        // other beans. The scheduled probe fires within 30s; until then the
        // app runs on the fallback cache, which is the correct behaviour.
        log.info("RedisHealthTracker initialised — first probe scheduled in 30s. " +
                "App will start on fallback cache if Redis is unreachable.");
    }

    // Scheduled probe — runs every 30s unconditionally.
    @Scheduled(fixedDelay = 30000)
    public void checkRedis() {
        boolean wasUp = redisUp.get();
        try {
            RedisConnection conn = connectionFactory.getConnection();
            conn.ping();
            conn.close();
            redisUp.set(true);
            lastCheckedAt.set(System.currentTimeMillis());
            if (!wasUp) {
                log.info("✅ Redis is back online — SmartCacheManager will resume Redis cache");
            }
        } catch (Exception e) {
            redisUp.set(false);
            lastCheckedAt.set(System.currentTimeMillis());
            if (wasUp) {
                log.warn("⚠️  Redis went offline — switching to in-memory fallback cache. " +
                        "Will retry every 30s. Error: {}", e.getMessage());
            } else {
                log.debug("Redis still unreachable — staying on fallback cache");
            }
        }
    }

    // Called by SafeRedisCache after an operation failure.
    // Only triggers a real probe if the last check was more than
    // ON_DEMAND_PROBE_COOLDOWN_MS ago — avoids hammering Redis (or the
    // network) with a probe on every single cache miss during an outage.
    public void reportFailure() {
        long now = System.currentTimeMillis();
        if (now - lastCheckedAt.get() > ON_DEMAND_PROBE_COOLDOWN_MS) {
            log.debug("On-demand Redis probe triggered by cache operation failure");
            checkRedis();
        } else {
            // Still within cooldown — just mark as down without probing
            redisUp.set(false);
        }
    }

    public boolean isRedisUp() {
        return redisUp.get();
    }
}