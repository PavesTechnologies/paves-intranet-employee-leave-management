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

/**
 * Service to handle logging job executions in a separate transaction.
 * This ensures that log entries (especially failures) are saved
 * even if the main job transaction rolls back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLoggingService {

    private final JobExecutionLogRepository jobExecutionLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobExecutionLog createJobLog(String jobName, String nodeId) {
        JobExecutionLog entry = JobExecutionLog.builder()
                .jobName(jobName)
                .status(JobStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .nodeIdentifier(nodeId)
                .attempt(1) // You could build logic to increment this on retries
                .build();

        JobExecutionLog saved = jobExecutionLogRepository.saveAndFlush(entry);
        log.info("🚀 (Log ID: {}) Starting job {} on node {}", saved.getId(), jobName, nodeId);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobLog(JobExecutionLog entry, boolean success, String errorMessage) {
        try {
            // Re-fetch the entity if it's detached, or just use the passed one if managed
            JobExecutionLog logEntry = jobExecutionLogRepository.findById(entry.getId())
                    .orElse(entry);

            logEntry.setStatus(success ? JobStatus.SUCCESS : JobStatus.FAILED);
            logEntry.setEndTime(LocalDateTime.now());
            logEntry.setDurationMs(Duration.between(logEntry.getStartTime(), logEntry.getEndTime()).toMillis());
            logEntry.setErrorMessage(errorMessage);

            jobExecutionLogRepository.saveAndFlush(logEntry);

            if (success) {
                log.info("✅ (Log ID: {}) Job '{}' completed successfully.", logEntry.getId(), logEntry.getJobName());
            } else {
                log.warn("⚠️ (Log ID: {}) Job '{}' failed: {}", logEntry.getId(), logEntry.getJobName(), errorMessage);
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to update job log {}: {}", entry.getId(), e.getMessage(), e);
        }
    }
}