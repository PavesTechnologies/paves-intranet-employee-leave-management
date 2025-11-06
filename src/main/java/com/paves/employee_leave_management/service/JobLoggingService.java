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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobExecutionLog createJobLog(String jobName, String nodeId) {
        log.info("Creating log for job: {} on node: {}", jobName, nodeId);
        JobExecutionLog entry = JobExecutionLog.builder()
                .jobName(jobName)
                .status(JobStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .nodeIdentifier(nodeId)
                .attempt(1) // Placeholder for retry logic
                .build();
        return jobExecutionLogRepository.saveAndFlush(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobLog(JobExecutionLog logEntry, boolean success, String errorMessage) {
        if (logEntry == null || logEntry.getId() == null) {
            log.error("CRITICAL: Attempted to update a null or un-persisted job log entry.");
            return;
        }

        String logId = logEntry.getId().toString();
        try {
            JobStatus finalStatus = success ? JobStatus.SUCCESS : JobStatus.FAILED;
            LocalDateTime endTime = LocalDateTime.now();
            long duration = Duration.between(logEntry.getStartTime(), endTime).toMillis();

            jobExecutionLogRepository.updateJobStatus(logId, finalStatus, endTime, duration, errorMessage);
            log.info("Successfully updated job log for job: {}", logEntry.getJobName());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to update job log for ID '{}': {}", logId, e.getMessage(), e);
        }
    }
}
