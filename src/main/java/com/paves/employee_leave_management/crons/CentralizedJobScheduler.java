package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.service.JobLoggingService;
import com.paves.employee_leave_management.service.LeaveBlockScheduler;
import com.paves.employee_leave_management.service.RecordLockServiceImple;
import com.paves.employee_leave_management.serviceInterface.AsyncNotificationServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceJobServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    private final LeaveBalanceJobServiceInterface leaveBalanceJobService;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Centralized_Daily_Master_Batch",
            lockAtLeastFor = "PT1H",
            lockAtMostFor = "PT6H")
    public void runDailyMasterBatch() {

        runJob("PROCESS-LEAVE-BLOCK", () ->
                leaveBlockScheduler.processLeaveBlock());

        runJob("ACTIVATE-PENDING-LEAVE-TYPES", () ->
                leaveBlockScheduler.activatePendingLeaveTypes());

        runJob("ACTIVATE-PENDING-GENDER-BASED-LEAVE-TYPES", () ->
                leaveBlockScheduler.activatePendingGenderBasedLeaveTypes());

        runJob("APPLY-SCHEDULED-LEAVE-TYPE-UPDATES", () ->
                leaveBlockScheduler.applyScheduledLeaveTypeUpdates());

        runJob("DEACTIVATE-DUE-LEAVE-TYPES", () ->
                leaveBlockScheduler.deactivateDueLeaveTypes());

        runJob("DEACTIVATE-DUE-GENDER-BASED-LEAVE-TYPES", () ->
                leaveBlockScheduler.deactivateDueGenderBasedLeaveTypes());

        runJob("EXPIRE-UNUSED-COMPOFFS", () ->
                leaveCompoffService.expireUnusedCompoffs());

        runJob("ACCRUAL-JOB", () ->
                leaveBalanceService.processAccrualForLeaveType());

        runJob("DAILY-LEAVE-DIGEST", () ->
                leaveBlockScheduler.sendDailyLeaveDigest());

        runJob("PENDING-APPROVAL-REMINDER", () ->
                sendPendingApprovalReminders());

        runJob("OVERDUE-APPROVAL-ESCALATION", () ->
                sendOverdueApprovalEscalations());

        runJob("DELETE-OLD-LOGS", () -> {
            int deleted = jobLoggingService.deleteOldJobLogs();
            log.info("Old job logs cleanup completed. {} entries deleted.", deleted);
        });
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @SchedulerLock(name = "Centralized_Frequent_Job",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT5M")
    @Transactional
    public void runFrequentJobs() {
        runJob("FREQUENT-5-MIN-JOB", () ->
                recordLockService.cleanupExpiredLocks());
    }

    // Resumes leave-balance jobs stuck in PENDING/RUNNING (worker thread died: restart, thread
    // pool rejection, uncaught exception) without needing a server restart — complements the
    // ApplicationReadyEvent listener that only fires on startup. Runs on its own schedule (not
    // inside runFrequentJobs) so an optimistic-lock conflict on one job can't roll back the
    // other job in that method.
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @SchedulerLock(name = "Resume_Stuck_Leave_Balance_Jobs",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT5M")
    public void resumeStuckLeaveBalanceJobs() {
        runJob("RESUME-STUCK-LEAVE-BALANCE-JOBS", () -> {
            List<LeaveBalanceJob> resumed = leaveBalanceJobService.claimStuckJobs();
            resumed.forEach(job ->
                    leaveBalanceJobService.processLeaveBalancesAsync(job.getJobId(), job.getLeaveTypeId()));
        });
    }

    private void sendPendingApprovalReminders() {
        List<LeaveRequest> pendingRequests = leaveRequestRepository
                .findByStatus(LeaveStatus.PENDING);

        if (pendingRequests.isEmpty()) return;

        Map<Employee, List<LeaveRequest>> requestsByManager = pendingRequests.stream()
                .filter(r -> r.getEmployee() != null)
                .filter(r -> r.getEmployee().getManager() != null)
                .collect(Collectors.groupingBy(r -> r.getEmployee().getManager()));

        if (requestsByManager.isEmpty()) return;

        for (Map.Entry<Employee, List<LeaveRequest>> entry : requestsByManager.entrySet()) {
            Employee manager = entry.getKey();

            // FIX — null email check
            if (manager.getEmail() == null || manager.getEmail().isBlank()) {
                log.warn("Manager {} {} has no email — skipping pending reminder",
                        manager.getFirstName(), manager.getLastName());
                continue;
            }

            List<LeaveRequest> requests = entry.getValue();
            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Pending Leave Approval Digest");
            templateModel.put("recipientName", manager.getFirstName());
            templateModel.put("messageBody",
                    "This is a digest of pending leave requests that require your approval.");
            templateModel.put("detailsTitle", "Pending Requests");
            templateModel.put("requests", requests);

            EmailDTO emailDTO = new EmailDTO(
                    manager.getEmail(),
                    "Pending Leave Approval Digest",
                    "pending-approval-digest.html",
                    true);
            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
        }
    }

    private void sendOverdueApprovalEscalations() {
        // FIX — overdue = pending for more than 3 days, not just all pending
        List<LeaveRequest> overdueRequests = leaveRequestRepository
                .findOverdueRequests(LocalDate.now().minusDays(3));

        if (overdueRequests.isEmpty()) return;

        Map<Employee, List<LeaveRequest>> requestsByManager = overdueRequests.stream()
                .filter(r -> r.getEmployee() != null)
                .filter(r -> r.getEmployee().getManager() != null)
                .collect(Collectors.groupingBy(r -> r.getEmployee().getManager()));

        if (requestsByManager.isEmpty()) return;

        for (Map.Entry<Employee, List<LeaveRequest>> entry : requestsByManager.entrySet()) {
            Employee manager = entry.getKey();

            // FIX — null email check
            if (manager.getEmail() == null || manager.getEmail().isBlank()) {
                log.warn("Manager {} {} has no email — skipping overdue escalation",
                        manager.getFirstName(), manager.getLastName());
                continue;
            }

            List<LeaveRequest> requests = entry.getValue();
            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Overdue Leave Approval Escalation Digest");
            templateModel.put("recipientName", manager.getFirstName());
            templateModel.put("messageBody",
                    "This is a digest of overdue leave requests that require your immediate attention.");
            templateModel.put("detailsTitle", "Overdue Requests");
            templateModel.put("requests", requests);

            EmailDTO emailDTO = new EmailDTO(
                    manager.getEmail(),
                    "Overdue Leave Approval Escalation Digest",
                    "overdue-approval-digest.html",
                    true);
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