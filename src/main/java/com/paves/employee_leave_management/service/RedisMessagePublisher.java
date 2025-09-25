package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.JobProgressDTO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisMessagePublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(JobProgressDTO message) {
        redisTemplate.convertAndSend("job-progress", message);
    }
}
