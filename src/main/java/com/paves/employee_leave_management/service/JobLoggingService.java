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

@Slf4j
@Service
@RequiredArgsConstructor
public class JobLoggingService {

    private final JobExecutionLogRepository jobExecutionLogRepository;

    /**
     * Creates a new job log entry when a job starts.
     * Runs in a separate transaction so it's persisted immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobExecutionLog createJobLog(String jobName, String nodeId) {
        log.info("Creating log for job '{}' on node '{}'", jobName, nodeId);

        JobExecutionLog entry = JobExecutionLog.builder()
                .jobName(jobName)
                .status(JobStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .nodeIdentifier(nodeId)
                .attempt(1) // Reserved for future retry logic
                .build();

        return jobExecutionLogRepository.saveAndFlush(entry);
    }

    /**
     * Updates the job log entry after execution completes or fails.
     * Runs in a separate transaction to ensure completion logging is persisted
     * even if the main job transaction fails or rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobLog(JobExecutionLog logEntry, boolean success, String errorMessage) {
        jobExecutionLogRepository.findById(logEntry.getId()).ifPresent(dbEntry -> {
            dbEntry.setStatus(success ? JobStatus.SUCCESS : JobStatus.FAILED);
            dbEntry.setEndTime(LocalDateTime.now());

            // Compute duration safely
            if (logEntry.getStartTime() != null && dbEntry.getEndTime() != null) {
                dbEntry.setDurationMs(Duration.between(logEntry.getStartTime(), dbEntry.getEndTime()).toMillis());
            }

            dbEntry.setErrorMessage(errorMessage);
            jobExecutionLogRepository.save(dbEntry);

            log.info("Job '{}' completed with status: {}{}",
                    dbEntry.getJobName(),
                    dbEntry.getStatus(),
                    errorMessage != null ? " | Error: " + errorMessage : "");
        });
    }
}
