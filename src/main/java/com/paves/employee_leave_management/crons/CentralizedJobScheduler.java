package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.entities.JobExecutionLog;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.repo.JobExecutionLogRepository;
import com.paves.employee_leave_management.service.*;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CentralizedJobScheduler {   // UPDATED NAME ✔

    private static final String NODE_ID = "NODE-" + UUID.randomUUID().toString().substring(0, 8);

    private final LeaveTypeRepo leaveTypeRepo;
    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final LeaveBlockScheduler leaveBlockScheduler;
    private final LeaveCompoffSerivceInterface leaveCompoffService;
    private final RecordLockServiceImple recordLockService;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final JobLoggingService jobLoggingService;

    /* ============================================================
       1) DAILY MASTER CRON (ENTERPRISE MAIN BATCH)
       ============================================================ */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Centralized_Daily_Master_Batch", lockAtLeastFor = "PT10S", lockAtMostFor = "PT30M")
    public void runDailyMasterBatch() {
        runJob("DAILY-MASTER-BATCH", () -> {

            LocalDate today = LocalDate.now();
            leaveBlockScheduler.processLeaveBlock();
            leaveBlockScheduler.activatePendingLeaveTypes();
            leaveBlockScheduler.deactivateDueLeaveTypes();
            leaveCompoffService.expireUnusedCompoffs();
            leaveBalanceService.processAccrualForLeaveType();



            int deleted = jobExecutionLogRepository.deleteByStartTimeBefore(LocalDateTime.now().minusWeeks(1));
            log.info("Old job logs cleanup completed. {} entries deleted.", deleted);
        });
    }

    /* ============================================================
       2) FREQUENT JOB — Runs every 5 minutes
       ============================================================ */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @SchedulerLock(name = "Centralized_Frequent_Job", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void runFrequentJobs() {
        runJob("FREQUENT-5-MIN-JOB", () -> {
            recordLockService.cleanupExpiredLocks();
        });
    }

    /* ============================================================
       CENTRAL LOGGING WRAPPER
       ============================================================ */
    private void runJob(String jobName, Runnable jobLogic) {
        JobExecutionLog logEntry = jobLoggingService.createJobLog(jobName, NODE_ID);
        boolean success = false;
        String error = null;

        try {
            jobLogic.run();
            success = true;
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobName, e.getMessage(), e);
            error = e.getMessage();
        } finally {
            jobLoggingService.updateJobLog(logEntry, success, error);
        }
    }
}
