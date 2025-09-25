package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.JobProgressDTO;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobProgressManager implements MessageListener {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitter register(String jobId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(jobId, emitter);

        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));

        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JobProgressDTO progress = objectMapper.readValue(message.getBody(), JobProgressDTO.class);
            SseEmitter emitter = emitters.get(progress.getJobId());

            if (emitter != null) {
                emitter.send(SseEmitter.event().name("progress").data(progress));

                if ("COMPLETED".equals(progress.getStatus()) || "FAILED".equals(progress.getStatus())) {
                    emitter.complete();
                }
            }
        } catch (IOException e) {
            // Handle exception
        }
    }
}
