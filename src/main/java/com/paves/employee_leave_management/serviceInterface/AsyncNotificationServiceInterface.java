package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.EmailDTO;

import java.util.concurrent.BlockingQueue;

public interface AsyncNotificationServiceInterface {
    void queueEmail(EmailDTO emailDTO);
    BlockingQueue<EmailDTO> getEmailQueue();
}
