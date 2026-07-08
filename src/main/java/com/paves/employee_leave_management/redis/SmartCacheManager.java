//package com.paves.employee_leave_management.redis;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.cache.Cache;
//import org.springframework.cache.CacheManager;
//import org.springframework.stereotype.Component;
//
//import java.util.Collection;
//
//@Component
//@Slf4j
//public class SmartCacheManager implements CacheManager {
//
//    private final CacheManager redisCacheManager;
//    private final CacheManager fallbackCacheManager;
//    private final RedisHealthTracker redisHealthTracker;
//
//    public SmartCacheManager(
//            @Qualifier("redisCacheManager") CacheManager redisCacheManager,
//            @Qualifier("fallbackCacheManager") CacheManager fallbackCacheManager,
//            RedisHealthTracker redisHealthTracker) {
//        this.redisCacheManager = redisCacheManager;
//        this.fallbackCacheManager = fallbackCacheManager;
//        this.redisHealthTracker = redisHealthTracker;
//    }
//
//    @Override
//    public Cache getCache(String name) {
//        if (redisHealthTracker.isRedisUp()) {
//            Cache redisCache = redisCacheManager.getCache(name);
//            if (redisCache != null) {
//                return new SafeRedisCache(redisCache,
//                        fallbackCacheManager.getCache(name),
//                        redisHealthTracker);
//            }
//        }
//        log.debug("Redis down — using in-memory cache for: {}", name);
//        return fallbackCacheManager.getCache(name);
//    }
//
//    @Override
//    public Collection<String> getCacheNames() {
//        return redisHealthTracker.isRedisUp()
//                ? redisCacheManager.getCacheNames()
//                : fallbackCacheManager.getCacheNames();
//    }
//}


package com.paves.employee_leave_management.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@Slf4j
public class SmartCacheManager implements CacheManager {

    private final CacheManager redisCacheManager;
    private final CacheManager fallbackCacheManager;
    private final RedisHealthTracker redisHealthTracker;

    public SmartCacheManager(
            @Qualifier("redisCacheManager") CacheManager redisCacheManager,
            @Qualifier("fallbackCacheManager") CacheManager fallbackCacheManager,
            RedisHealthTracker redisHealthTracker) {
        this.redisCacheManager = redisCacheManager;
        this.fallbackCacheManager = fallbackCacheManager;
        this.redisHealthTracker = redisHealthTracker;
    }

    @Override
    public Cache getCache(String name) {
        boolean up = redisHealthTracker.isRedisUp();
        log.info("getCache('{}') -> redisUp={}", name, up);
        if (redisHealthTracker.isRedisUp()) {
            Cache redisCache = redisCacheManager.getCache(name);
            if (redisCache != null) {
                // Wrap in SafeRedisCache so any runtime Redis failure
                // transparently falls back without throwing to the caller.
                return new SafeRedisCache(redisCache,
                        fallbackCacheManager.getCache(name),
                        redisHealthTracker);
            }
        }
        log.debug("Redis unavailable — serving '{}' from in-memory fallback cache", name);
        return fallbackCacheManager.getCache(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return redisHealthTracker.isRedisUp()
                ? redisCacheManager.getCacheNames()
                : fallbackCacheManager.getCacheNames();
    }
}