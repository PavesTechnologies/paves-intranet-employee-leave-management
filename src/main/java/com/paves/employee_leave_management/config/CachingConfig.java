package com.paves.employee_leave_management.config;

import com.paves.employee_leave_management.redis.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class CachingConfig implements CachingConfigurer {


    private final SmartCacheManager smartCacheManager;

    public CachingConfig(SmartCacheManager smartCacheManager) {
        this.smartCacheManager = smartCacheManager;
    }

    @Override
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return smartCacheManager;
    }







// old one

//    private final CacheManager fallbackCacheManager;

//    public CachingConfig(CacheManager fallbackCacheManager) {
//        this.fallbackCacheManager = fallbackCacheManager;
//    }

//    @Override
//    public CacheErrorHandler errorHandler() {
//        return new CacheErrorHandler() {
//
//            @Override
//            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
//                log.warn("Redis GET failed for key: {} — falling back to in-memory cache. Error: {}",
//                        key, e.getMessage());
//                // Fallback cache will serve the request
//                Cache fallback = fallbackCacheManager.getCache(cache.getName());
//                if (fallback != null) fallback.get(key);
//            }
//
//            @Override
//            public void handleCachePutError(RuntimeException e, Cache cache,
//                                            Object key, Object value) {
//                log.warn("Redis PUT failed for key: {} — storing in fallback cache. Error: {}",
//                        key, e.getMessage());
//                Cache fallback = fallbackCacheManager.getCache(cache.getName());
//                if (fallback != null) fallback.put(key, value);
//            }
//
//            @Override
//            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
//                log.warn("Redis EVICT failed for key: {} — evicting from fallback. Error: {}",
//                        key, e.getMessage());
//                Cache fallback = fallbackCacheManager.getCache(cache.getName());
//                if (fallback != null) fallback.evict(key);
//            }
//
//            @Override
//            public void handleCacheClearError(RuntimeException e, Cache cache) {
//                log.warn("Redis CLEAR failed — clearing fallback cache. Error: {}",
//                        e.getMessage());
//                Cache fallback = fallbackCacheManager.getCache(cache.getName());
//                if (fallback != null) fallback.clear();
//            }
//        };
//    }
}