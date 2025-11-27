package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.service.JobLoggingService;
import com.paves.employee_leave_management.service.LeaveBlockScheduler;
import com.paves.employee_leave_management.service.RecordLockServiceImple;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
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
    private final EmailServiceInterface emailService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Centralized_Daily_Master_Batch", lockAtLeastFor = "PT10S", lockAtMostFor = "PT30M")
    public void runDailyMasterBatch() {
        runJob("DAILY-MASTER-BATCH", () -> {
            leaveBlockScheduler.processLeaveBlock();
            leaveBlockScheduler.activatePendingLeaveTypes();
            leaveBlockScheduler.deactivateDueLeaveTypes();
            leaveCompoffService.expireUnusedCompoffs();
            leaveBalanceService.processAccrualForLeaveType();
            leaveBlockScheduler.sendDailyLeaveDigest();
            sendPendingApprovalReminders();
            sendOverdueApprovalEscalations();

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
        Map<Employee, List<LeaveRequest>> requestsByManager = pendingRequests.stream()
                .filter(request -> request.getEmployee().getManager() != null)
                .collect(Collectors.groupingBy(request -> request.getEmployee().getManager()));

        for (Map.Entry<Employee, List<LeaveRequest>> entry : requestsByManager.entrySet()) {
            Employee manager = entry.getKey();
            List<LeaveRequest> requests = entry.getValue();
            emailService.sendPendingApprovalReminderDigest(manager.getEmail(), requests);
        }
    }

    private void sendOverdueApprovalEscalations() {
        List<LeaveRequest> overdueRequests = leaveRequestRepository.findByStatus(LeaveStatus.PENDING);
        Map<Employee, List<LeaveRequest>> requestsByManager = overdueRequests.stream()
                .filter(request -> request.getEmployee().getManager() != null)
                .collect(Collectors.groupingBy(request -> request.getEmployee().getManager()));

        for (Map.Entry<Employee, List<LeaveRequest>> entry : requestsByManager.entrySet()) {
            Employee manager = entry.getKey();
            List<LeaveRequest> requests = entry.getValue();
            emailService.sendOverdueApprovalEscalationDigest(manager.getEmail(), requests);
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