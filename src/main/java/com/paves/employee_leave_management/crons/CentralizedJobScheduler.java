package com.paves.employee_leave_management.crons;

import com.paves.employee_leave_management.entities.JobExecutionLog;
import com.paves.employee_leave_management.enums.JobStatus;
import com.paves.employee_leave_management.repositories.JobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CentralizedJobScheduler {

    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final ApplicationContext applicationContext;
    private static final String NODE_ID = "NODE-1"; // useful in clusters

    // ---------------- Thread Pool ----------------
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("JobExecutor-");
        executor.initialize();
        return executor;
    }

    // ---------------- Cron Jobs ----------------

    //  Daily tasks at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduleDailyTasks() {
        runJob("Daily-Leave-Block", this::processLeaveBlock);
        runJob("Daily-Activate-Leaves", this::activatePendingLeaveTypes);
        runJob("Daily-Deactivate-Leaves", this::deactivateDueLeaveTypes);
        runJob("Daily-Compoff-Expiry", this::expireUnusedCompoffs);
    }

    // Monthly tasks at 00:05 on 1st of every month
    @Scheduled(cron = "0 5 0 1 * *")
    public void scheduleMonthlyTasks() {
        runJob("Monthly-Leave-Accrual", this::scheduleMonthlyLeaveAccrual);
    }

    //  Yearly tasks (1st Jan)
    @Scheduled(cron = "0 0 0 1 1 *")
    public void scheduleYearlyTasks() {
        runJob("Yearly-Leave-Close", this::scheduleYearEndProcessing);
    }

    // Frequent tasks (every 5 minutes)
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void scheduleFrequentTasks() {
        runJob("Frequent-RecordLock-Cleanup", this::cleanupExpiredLocks);
    }

    // Clean old job logs hourly
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupOldJobLogs() {
        runJob("Cleanup-Old-Job-Logs", this::deleteOldJobLogs);
    }

    // ---------------- Job Orchestration ----------------

    private void runJob(String jobName, Supplier<Boolean> jobFunction) {
        try {
            UUID jobId = createJobLog(jobName);
            CentralizedJobScheduler self = applicationContext.getBean(CentralizedJobScheduler.class);
            taskExecutor().execute(() -> self.executeAndUpdate(jobId, jobName, jobFunction));
        } catch (Exception e) {
            log.error("Failed to start job {}: {}", jobName, e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected UUID createJobLog(String jobName) {
        JobExecutionLog entry = JobExecutionLog.builder()
                .jobName(jobName)
                .status(JobStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .attempt(1)
                .nodeIdentifier(NODE_ID)
                .build();

        JobExecutionLog saved = jobExecutionLogRepository.saveAndFlush(entry);
        log.info("Started job {} (ID: {})", jobName, saved.getId());
        return saved.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void executeAndUpdate(UUID jobId, String jobName, Supplier<Boolean> jobFunction) {
        boolean success = false;
        String errorMessage = null;
        LocalDateTime startTime = LocalDateTime.now();

        try {
            success = jobFunction.get();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Job {} failed: {}", jobName, e.getMessage(), e);
        }

        try {
            updateJobLog(jobId, success, errorMessage, startTime);
        } catch (Exception e) {
            log.error("Failed to update job {}: {}", jobName, e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateJobLog(UUID jobId, boolean success, String errorMessage, LocalDateTime startTime) {
        jobExecutionLogRepository.findById(jobId).ifPresent(entry -> {
            entry.setStatus(success ? JobStatus.SUCCESS : JobStatus.FAILED);
            entry.setEndTime(LocalDateTime.now());
            entry.setDurationMs(java.time.Duration.between(startTime, entry.getEndTime()).toMillis());
            entry.setErrorMessage(errorMessage);
            jobExecutionLogRepository.saveAndFlush(entry);

            log.info("Job '{}' completed with status {}", entry.getJobName(), entry.getStatus());
        });
    }

    // ---------------- Actual Job Implementations ----------------

    private boolean processLeaveBlock() {
        log.info("Processing leave block...");
        // leaveBlockScheduler.processLeaveBlock();
        return true;
    }

    private boolean activatePendingLeaveTypes() {
        log.info("Activating pending leave types...");
        // leaveBlockScheduler.activatePendingLeaveTypes();
        return true;
    }

    private boolean deactivateDueLeaveTypes() {
        log.info("Deactivating due leave types...");
        // leaveBlockScheduler.deactivateDueLeaveTypes();
        return true;
    }

    private boolean expireUnusedCompoffs() {
        log.info("Expiring unused comp-offs...");
        // compoffExpiryScheduler.expireUnusedCompoffs();
        return true;
    }

    private boolean scheduleMonthlyLeaveAccrual() {
        log.info("Running monthly leave accrual...");
        // leaveBalanceService.scheduleMonthlyLeaveAccrual();
        return true;
    }

    private boolean scheduleYearEndProcessing() {
        log.info("Running year-end leave balance processing...");
        // leaveBalanceService.scheduleYearEndProcessing();
        return true;
    }

    private boolean cleanupExpiredLocks() {
        log.info("Cleaning expired record locks...");
        // recordLockService.cleanupExpiredLocks();
        return true;
    }

    private boolean deleteOldJobLogs() {
        LocalDateTime cutoffDate = LocalDate.now().minusMonths(2).atStartOfDay();
        try {
            int deletedCount = jobExecutionLogRepository.deleteByStartTimeBefore(cutoffDate);
            log.info("🧹 Deleted {} job logs older than {}", deletedCount, cutoffDate);
            return true;
        } catch (Exception e) {
            log.error("Error deleting old job logs: {}", e.getMessage(), e);
            return false;
        }
    }
}
