package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.JobExecutionLog;
import com.paves.employee_leave_management.enums.JobStatus;
import com.paves.employee_leave_management.repo.JobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobLoggingService {

    private final JobExecutionLogRepository jobExecutionLogRepository;
    private static final int LOG_RETENTION_DAYS = 30;


    /**
     * Creates a new job log entry when a job starts.
     * Runs in a separate transaction so it's persisted immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createJobLog(String jobName, String nodeId) {
        log.info("Creating log for job '{}' on node '{}'", jobName, nodeId);

        JobExecutionLog entry = JobExecutionLog.builder()
                .jobName(jobName)
                .status(JobStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .nodeIdentifier(nodeId)
                .attempt(1) // Reserved for future retry logic
                .build();

        JobExecutionLog saved = jobExecutionLogRepository.saveAndFlush(entry);
        return saved.getId();
    }

    /**
     * Updates the job log entry after execution completes or fails.
     * Runs in a separate transaction to ensure completion logging is persisted
     * even if the main job transaction fails or rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobLog(UUID logId, boolean success, String errorMessage) {
        jobExecutionLogRepository.findById(logId).ifPresent(dbEntry -> {
            dbEntry.setStatus(success ? JobStatus.SUCCESS : JobStatus.FAILED);
            dbEntry.setEndTime(LocalDateTime.now());

            // Compute duration safely
            if (dbEntry.getStartTime() != null && dbEntry.getEndTime() != null) {
                dbEntry.setDurationMs(Duration.between(dbEntry.getStartTime(), dbEntry.getEndTime()).toMillis());
            }

            dbEntry.setErrorMessage(errorMessage);
            jobExecutionLogRepository.save(dbEntry);

            log.info("Job '{}' completed with status: {}{}",
                    dbEntry.getJobName(),
                    dbEntry.getStatus(),
                    errorMessage != null ? " | Error: " + errorMessage : "");
        });
    }

    /**
     * Deletes job logs older than a configured retention period.
     * @return The number of deleted log entries.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteOldJobLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(LOG_RETENTION_DAYS);
        log.info("Deleting job logs older than {}", cutoff);
        return jobExecutionLogRepository.deleteByStartTimeBefore(cutoff);
    }
}
