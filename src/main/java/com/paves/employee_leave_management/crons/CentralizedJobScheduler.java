package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.entities.JobExecutionLog;
import com.paves.employee_leave_management.repo.JobExecutionLogRepository;
import com.paves.employee_leave_management.service.JobLoggingService;
import com.paves.employee_leave_management.service.LeaveBlockScheduler;
import com.paves.employee_leave_management.service.LeaveRequestService;
import com.paves.employee_leave_management.service.RecordLockServiceImple;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CentralizedJobScheduler {

    // Unique identifier for this application instance
    private static final String NODE_ID = "NODE-" + UUID.randomUUID().toString().substring(0, 8);

    // Lock configuration
    private static final String LOCK_AT_LEAST_10S = "PT10S";
    private static final String LOCK_AT_MOST_15M = "PT15M";

    // Injected services
    private final LeaveBlockScheduler leaveBlockScheduler;
    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final LeaveCompoffSerivceInterface leaveCompoffService;
    private final RecordLockServiceImple recordLockService;
    private final JobLoggingService jobLoggingService;
    private final JobExecutionLogRepository jobExecutionLogRepository;

    /* ---------------- DAILY JOBS ---------------- */

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_LeaveBlock", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyLeaveBlock() {
        runJob(leaveBlockScheduler::processLeaveBlock, "Daily-Leave-Block");
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_ActivateLeaves", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyActivateLeaves() {
        runJob(leaveBlockScheduler::activatePendingLeaveTypes, "Daily-Activate-Leaves");
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_DeactivateLeaves", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyDeactivateLeaves() {
        runJob(leaveBlockScheduler::deactivateDueLeaveTypes, "Daily-Deactivate-Leaves");
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_CompoffExpiry", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyCompoffExpiry() {
        runJob(leaveCompoffService::expireUnusedCompoffs, "Daily-Compoff-Expiry");
    }

    /* ---------------- MONTHLY JOB ---------------- */

    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Monthly_LeaveAccrual", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = "PT1H")
    public void scheduleMonthlyLeaveAccrual() {
        runJob(leaveBalanceService::triggerMonthlyLeaveAccrual, "Monthly-Leave-Accrual");
    }

    /* ---------------- YEARLY JOB ---------------- */

    @Scheduled(cron = "0 0 0 1 1 *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Yearly_LeaveClose", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = "PT2H")
    public void scheduleYearlyLeaveClose() {
        runJob(leaveBalanceService::processYearEndCarryForward, "Yearly-Leave-Close");
    }

    /* ---------------- FREQUENT JOB ---------------- */

    @Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
    @SchedulerLock(name = "Frequent_RecordLockCleanup", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void scheduleFrequentRecordLockCleanup() {
        runJob(recordLockService::cleanupExpiredLocks, "Frequent-RecordLock-Cleanup");
    }

    /* ---------------- LOG CLEANUP JOB ---------------- */

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata") // daily midnight
    @SchedulerLock(name = "Cleanup_OldJobLogs", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void cleanupOldJobLogs() {
        runJob(() -> {
            int deleted = jobExecutionLogRepository.deleteByStartTimeBefore(LocalDateTime.now().minusWeeks(1));
            log.info("Old job logs cleanup completed. {} entries deleted.", deleted);
        }, "Cleanup-Old-Job-Logs");
    }

    /* ---------------- CENTRAL RUNNER ---------------- */

    /**
     * Wraps any job in consistent logging and error handling.
     * Ensures every job has a start, success/failure, and duration recorded.
     */
    private void runJob(Runnable jobFunction, String jobName) {
        JobExecutionLog logEntry = jobLoggingService.createJobLog(jobName, NODE_ID);
        boolean success = false;
        String errorMessage = null;

        try {
            jobFunction.run();
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Job '{}' failed: {}", jobName, errorMessage, e);
        } finally {
            jobLoggingService.updateJobLog(logEntry, success, errorMessage);
        }
    }
}
