package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.service.JobLoggingService;
import com.paves.employee_leave_management.service.LeaveBlockScheduler;
import com.paves.employee_leave_management.service.RecordLockServiceImple;
import com.paves.employee_leave_management.serviceInterface.AsyncNotificationServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CentralizedJobScheduler {

    private static final String NODE_ID = "NODE-" + UUID.randomUUID().toString().substring(0, 8);

    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final LeaveBlockScheduler leaveBlockScheduler;
    private final LeaveCompoffSerivceInterface leaveCompoffService;
    private final RecordLockServiceImple recordLockService;
    private final JobLoggingService jobLoggingService;
    private final LeaveRequestRepo leaveRequestRepository;
    private final AsyncNotificationServiceInterface asyncNotificationService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Centralized_Daily_Master_Batch",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT30M")
    public void runDailyMasterBatch() {

        runJob("PROCESS-LEAVE-BLOCK", () -> {
            leaveBlockScheduler.processLeaveBlock();
        });

        runJob("ACTIVATE-PENDING-LEAVE-TYPES", () -> {
            leaveBlockScheduler.activatePendingLeaveTypes();
        });

        runJob("DEACTIVATE-DUE-LEAVE-TYPES", () -> {
            leaveBlockScheduler.deactivateDueLeaveTypes();
        });

        runJob("EXPIRE-UNUSED-COMPOFFS", () -> {
            leaveCompoffService.expireUnusedCompoffs();
        });

        runJob("ACCRUAL-JOB", () -> {
            leaveBalanceService.processAccrualForLeaveType();
        });

        runJob("DAILY-LEAVE-DIGEST", () -> {
            leaveBlockScheduler.sendDailyLeaveDigest();
        });

        runJob("PENDING-APPROVAL-REMINDER", () -> {
            sendPendingApprovalReminders();
        });

        runJob("OVERDUE-APPROVAL-ESCALATION", () -> {
            sendOverdueApprovalEscalations();
        });

        runJob("DELETE-OLD-LOGS", () -> {
            int deleted = jobLoggingService.deleteOldJobLogs();
            log.info("Old job logs cleanup completed. {} entries deleted.", deleted);
        });
    }


    @Scheduled(fixedRate = 5 * 60 * 1000)
    @SchedulerLock(name = "Centralized_Frequent_Job", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void runFrequentJobs() {
        runJob("FREQUENT-5-MIN-JOB", () -> {
            recordLockService.cleanupExpiredLocks();
        });
    }

    private void sendPendingApprovalReminders() {
        List<LeaveRequest> pendingRequests = leaveRequestRepository.findByStatus(LeaveStatus.PENDING);
        if (pendingRequests.isEmpty()) {
            return;
        }

        Map<Employee, List<LeaveRequest>> requestsByManager = pendingRequests.stream()
                .filter(request -> request.getEmployee().getManager() != null)
                .collect(Collectors.groupingBy(request -> request.getEmployee().getManager()));

        if (requestsByManager.isEmpty()) {
            return;
        }

        for (Map.Entry<Employee, List<LeaveRequest>> entry : requestsByManager.entrySet()) {
            Employee manager = entry.getKey();
            List<LeaveRequest> requests = entry.getValue();
            Map<String, Object> templateModel = new java.util.LinkedHashMap<>();
            templateModel.put("title", "Pending Leave Approval Digest");
            templateModel.put("recipientName", manager.getFirstName());
            templateModel.put("messageBody", "This is a digest of pending leave requests that require your approval.");
            templateModel.put("detailsTitle", "Pending Requests");
            templateModel.put("requests", requests);
            EmailDTO emailDTO = new EmailDTO(manager.getEmail(), "Pending Leave Approval Digest", "pending-approval-digest.html", true);
            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
        }
    }

    private void sendOverdueApprovalEscalations() {
        List<LeaveRequest> overdueRequests = leaveRequestRepository.findByStatus(LeaveStatus.PENDING);
        if (overdueRequests.isEmpty()) {
            return;
        }

        Map<Employee, List<LeaveRequest>> requestsByManager = overdueRequests.stream()
                .filter(request -> request.getEmployee().getManager() != null)
                .collect(Collectors.groupingBy(request -> request.getEmployee().getManager()));

        if (requestsByManager.isEmpty()) {
            return;
        }

        for (Map.Entry<Employee, List<LeaveRequest>> entry : requestsByManager.entrySet()) {
            Employee manager = entry.getKey();
            List<LeaveRequest> requests = entry.getValue();
            Map<String, Object> templateModel = new java.util.LinkedHashMap<>();
            templateModel.put("title", "Overdue Leave Approval Escalation Digest");
            templateModel.put("recipientName", manager.getFirstName());
            templateModel.put("messageBody", "This is a digest of overdue leave requests that require your immediate attention.");
            templateModel.put("detailsTitle", "Overdue Requests");
            templateModel.put("requests", requests);
            EmailDTO emailDTO = new EmailDTO(manager.getEmail(), "Overdue Leave Approval Escalation Digest", "overdue-approval-digest.html", true);
            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
        }
    }

    private void runJob(String jobName, Runnable jobLogic) {
        UUID logId = jobLoggingService.createJobLog(jobName, NODE_ID);
        boolean success = false;
        String error = null;

        try {
            jobLogic.run();
            success = true;
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobName, e.getMessage(), e);
            error = e.getMessage();
        } finally {
            jobLoggingService.updateJobLog(logId, success, error);
        }
    }
}