//package com.paves.employee_leave_management.redis;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.cache.Cache;
//
//import java.util.concurrent.Callable;
//
//
//
//@Slf4j
//public class SafeRedisCache implements Cache {
//
//    private final Cache redisCache;
//    private final Cache fallbackCache;
//    private final RedisHealthTracker redisHealthTracker;
//
//    public SafeRedisCache(Cache redisCache, Cache fallbackCache,
//                          RedisHealthTracker redisHealthTracker) {
//        this.redisCache = redisCache;
//        this.fallbackCache = fallbackCache;
//        this.redisHealthTracker = redisHealthTracker;
//    }
//
//    @Override
//    public ValueWrapper get(Object key) {
//        try {
//            return redisCache.get(key);
//        } catch (Exception e) {
//            log.warn("Redis GET failed for key: {} — using fallback. Error: {}",
//                    key, e.getMessage());
//            redisHealthTracker.checkRedis();
//            return fallbackCache != null ? fallbackCache.get(key) : null;
//        }
//    }
//
//    @Override
//    public <T> T get(Object key, Class<T> type) {
//        try {
//            return redisCache.get(key, type);
//        } catch (Exception e) {
//            log.warn("Redis GET failed for key: {} — using fallback", key);
//            redisHealthTracker.checkRedis();
//            return fallbackCache != null ? fallbackCache.get(key, type) : null;
//        }
//    }
//
//    @Override
//    public <T> T get(Object key, Callable<T> valueLoader) {
//        try {
//            return redisCache.get(key, valueLoader);
//        } catch (Exception e) {
//            log.warn("Redis GET failed for key: {} — using fallback", key);
//            redisHealthTracker.checkRedis();
//            return fallbackCache != null
//                    ? fallbackCache.get(key, valueLoader)
//                    : loadDirectly(valueLoader);
//        }
//    }
//
//    @Override
//    public void put(Object key, Object value) {
//        try {
//            redisCache.put(key, value);
//        } catch (Exception e) {
//            log.warn("Redis PUT failed for key: {} — storing in fallback", key);
//            redisHealthTracker.checkRedis();
//            if (fallbackCache != null) fallbackCache.put(key, value);
//        }
//    }
//
//    @Override
//    public void evict(Object key) {
//        try {
//            redisCache.evict(key);
//        } catch (Exception e) {
//            log.warn("Redis EVICT failed for key: {} — evicting from fallback", key);
//            if (fallbackCache != null) fallbackCache.evict(key);
//        }
//    }
//
//    @Override
//    public void clear() {
//        try {
//            redisCache.clear();
//        } catch (Exception e) {
//            log.warn("Redis CLEAR failed — clearing fallback");
//            if (fallbackCache != null) fallbackCache.clear();
//        }
//    }
//
//    @Override
//    public String getName() {
//        return redisCache.getName();
//    }
//
//    @Override
//    public Object getNativeCache() {
//        return redisCache.getNativeCache();
//    }
//
//    private <T> T loadDirectly(Callable<T> valueLoader) {
//        try {
//            return valueLoader.call();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to load value directly from DB", e);
//        }
//    }
//}


package com.paves.employee_leave_management.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

@Slf4j
public class SafeRedisCache implements Cache {

    private final Cache redisCache;
    private final Cache fallbackCache;
    private final RedisHealthTracker redisHealthTracker;

    public SafeRedisCache(Cache redisCache, Cache fallbackCache,
                          RedisHealthTracker redisHealthTracker) {
        this.redisCache = redisCache;
        this.fallbackCache = fallbackCache;
        this.redisHealthTracker = redisHealthTracker;
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return redisCache.get(key);
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}' — using fallback. Error: {}",
                    key, e.getMessage());
            // reportFailure() instead of checkRedis() — respects the 10s cooldown
            // so a sustained outage doesn't trigger a blocking probe on every miss.
            redisHealthTracker.reportFailure();
            return fallbackCache != null ? fallbackCache.get(key) : null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return redisCache.get(key, type);
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}' — using fallback", key);
            redisHealthTracker.reportFailure();
            return fallbackCache != null ? fallbackCache.get(key, type) : null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            return redisCache.get(key, valueLoader);
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}' — using fallback", key);
            redisHealthTracker.reportFailure();
            return fallbackCache != null
                    ? fallbackCache.get(key, valueLoader)
                    : loadDirectly(valueLoader);
        }
    }

    @Override
    public void put(Object key, Object value) {
        try {
            redisCache.put(key, value);
        } catch (Exception e) {
            log.warn("Redis PUT failed for key '{}' — storing in fallback", key);
            redisHealthTracker.reportFailure();
            if (fallbackCache != null) fallbackCache.put(key, value);
        }
    }

    @Override
    public void evict(Object key) {
        try {
            redisCache.evict(key);
        } catch (Exception e) {
            log.warn("Redis EVICT failed for key '{}'", key, e);
            redisHealthTracker.reportFailure();
        } finally {
            // Always evict fallback too — not just on Redis failure.
            // An approve/reject/save must invalidate whichever store
            // most recently served a read for this key, and we can't
            // know which store that was, so evict both, always.
            if (fallbackCache != null) {
                fallbackCache.evict(key);
            }
        }
    }

    @Override
    public void clear() {
        try {
            redisCache.clear();
        } catch (Exception e) {
            log.warn("Redis CLEAR failed for cache '{}'", getName(), e);
            redisHealthTracker.reportFailure();
        } finally {
            if (fallbackCache != null) {
                fallbackCache.clear();
            }
        }
    }

    @Override
    public String getName() {
        return redisCache.getName();
    }

    @Override
    public Object getNativeCache() {
        return redisCache.getNativeCache();
    }

    private <T> T loadDirectly(Callable<T> valueLoader) {
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load value from DB after Redis and fallback both unavailable", e);
        }
    }
}