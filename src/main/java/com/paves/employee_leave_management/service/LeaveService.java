package com.paves.employee_leave_management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class LeaveService {

    @Autowired
    private CacheManager redisCacheManager;

    @Autowired
    private CacheManager fallbackCacheManager;

    public CacheManager getSafeCacheManager() {
        try {
            redisCacheManager.getCache("test").get("key");
            return redisCacheManager;
        } catch (Exception e) {
            return fallbackCacheManager;
        }
    }
}
