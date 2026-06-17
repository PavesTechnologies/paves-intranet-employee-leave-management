package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.serviceInterface.AsyncNotificationServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncNotificationService implements AsyncNotificationServiceInterface {

    private final BlockingQueue<EmailDTO> emailQueue = new LinkedBlockingQueue<>();

    @Override
    public void queueEmail(EmailDTO emailDTO) {
        try {
            log.info("Queuing email to: {} with subject: {}", emailDTO.getTo(), emailDTO.getSubject());
            emailQueue.put(emailDTO);
        } catch (InterruptedException e) {
            log.error("Error while queuing email", e);
            Thread.currentThread().interrupt();
        }
    }

    public BlockingQueue<EmailDTO> getEmailQueue() {
        return emailQueue;
    }
}