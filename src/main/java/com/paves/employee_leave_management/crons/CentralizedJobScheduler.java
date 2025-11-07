package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.entities.JobExecutionLog;
import com.paves.employee_leave_management.enums.JobStatus; // Assuming this import path
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CentralizedJobScheduler {

    // Unique identifier for this application instance
    private static final String NODE_ID = "NODE-" + UUID.randomUUID().toString().substring(0, 8);

    // ShedLock handles the locking
    private final LeaveBlockScheduler leaveBlockScheduler;
    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final LeaveCompoffSerivceInterface leaveCompoffService;
    private final RecordLockServiceImple recordLockService;

    // We inject our new service to handle custom logging
    private final JobLoggingService jobLoggingService;

    // This repository is now only needed for the cleanup task
    private final JobExecutionLogRepository jobExecutionLogRepository;


    // ---------------- CRONS ----------------
    private static final String LOCK_AT_LEAST_10S = "PT10S";
    private static final String LOCK_AT_MOST_15M = "PT15M";

    @Scheduled(cron = "0 0 0 * * *",zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_LeaveBlock", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyLeaveBlock() {
        runJob(leaveBlockScheduler::processLeaveBlock, "Daily-Leave-Block");
    }

    @Scheduled(cron = "0 0 0 * * *",zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_ActivateLeaves", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyActivateLeaves() {
        runJob(leaveBlockScheduler::activatePendingLeaveTypes, "Daily-Activate-Leaves");
    }

    @Scheduled(cron = "0 0 0 * * *",zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_DeactivateLeaves", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyDeactivateLeaves() {
        runJob(leaveBlockScheduler::deactivateDueLeaveTypes, "Daily-Deactivate-Leaves");
    }

    @Scheduled(cron = "0 0 0 * * *",zone = "Asia/Kolkata")
    @SchedulerLock(name = "DailyTasks_CompoffExpiry", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void scheduleDailyCompoffExpiry() {
        runJob(leaveCompoffService::expireUnusedCompoffs, "Daily-Compoff-Expiry");
    }

    @Scheduled(cron = "0 5 0 1 * *",zone = "Asia/Kolkata")
    @SchedulerLock(name = "Monthly_LeaveAccrual", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = "PT1H")
    public void scheduleMonthlyLeaveAccrual() {
        runJob(leaveBalanceService::triggerMonthlyLeaveAccrual, "Monthly-Leave-Accrual");
    }

    @Scheduled(cron = "0 0 0 1 1 *",zone = "Asia/Kolkata")
    @SchedulerLock(name = "Yearly_LeaveClose", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = "PT2H")
    public void scheduleYearlyLeaveClose() {
        runJob(leaveBalanceService::processYearEndCarryForward, "Yearly-Leave-Close");
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @SchedulerLock(name = "Frequent_RecordLockCleanup", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void scheduleFrequentRecordLockCleanup() {
        runJob(recordLockService::cleanupExpiredLocks, "Frequent-RecordLock-Cleanup");
    }

    /**
     * This task cleans up its *own* log table.
     * ShedLock ensures it only runs on one node.
     */
     // Runs hourly

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "Cleanup_OldJobLogs", lockAtLeastFor = LOCK_AT_LEAST_10S, lockAtMostFor = LOCK_AT_MOST_15M)
    public void cleanupOldJobLogs() {
        // We wrap this special job in the same logging mechanism
        runJob(() -> {
            int deleted = jobExecutionLogRepository.deleteByStartTimeBefore(LocalDateTime.now().minusWeeks(1));
        }, "Cleanup-Old-Job-Logs");
    }

    /**
     * A wrapper to run the job, now with custom logging.
     * ShedLock ensures this *method* is only called on one node.
     */
    private void runJob(Runnable jobFunction, String jobName) {
        // 1. Create the log entry. This runs in its own transaction.
        JobExecutionLog logEntry = jobLoggingService.createJobLog(jobName, NODE_ID);

        boolean success = false;
        String errorMessage = null;

        try {
            // 2. Run the actual business logic
            jobFunction.run();
            success = true;
        } catch (Exception e) {

            errorMessage = e.getMessage();
        } finally {
            // 3. Update the log entry. This also runs in its own transaction.
            jobLoggingService.updateJobLog(logEntry, success, errorMessage);
        }
    }
}