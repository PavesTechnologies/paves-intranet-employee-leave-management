package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.serviceInterface.AsyncNotificationServiceInterface;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailQueueProcessor {

    private final AsyncNotificationServiceInterface asyncNotificationService;
    private final EmailServiceInterface emailService;
    private final SpringTemplateEngine templateEngine;

    @Scheduled(fixedRate = 60000) // Run every minute
    public void processEmailQueue() {
        log.info("Processing email queue...");
        List<EmailDTO> emails = new ArrayList<>();
        asyncNotificationService.getEmailQueue().drainTo(emails, 100); // Drain up to 100 emails

        if (emails.isEmpty()) {
            log.info("Email queue is empty.");
            return;
        }

        log.info("Sending {} emails from the queue.", emails.size());
        for (EmailDTO email : emails) {
            if (email.getTemplateModel() != null) {
                Context context = new Context();
                context.setVariables(email.getTemplateModel());
                String htmlBody = templateEngine.process(email.getBody(), context);
                email.setBody(htmlBody);
            }
            emailService.sendEmail(email);
        }
        log.info("Finished processing email queue.");
    }
}