package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.EmployeeCdcEvent;
import com.paves.employee_leave_management.entities.CdcFailureLog;
import com.paves.employee_leave_management.entities.CdcFailureLog.FailureType;
import com.paves.employee_leave_management.entities.CdcFailureLog.CdcFailureStatus;
import com.paves.employee_leave_management.repo.CdcFailureLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdcFailureLogService {

    private final CdcFailureLogRepository cdcFailureLogRepository;
    private final ObjectMapper objectMapper;

    // log a failure from a Kafka record
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CdcFailureLog logFailure(
            ConsumerRecord<String, String> record,
            EmployeeCdcEvent event,
            FailureType failureType,
            Exception exception) {

        CdcFailureLog log = CdcFailureLog.builder()
                .employeeUuid(event != null ? event.getEmployeeUuid() : null)
                .employeeId(event != null ? event.getEmployeeId() : null)
                .operation(event != null ? event.getOp() : null)
                .failureType(failureType)
                .errorMessage(exception.getMessage())
                .stackTrace(getStackTrace(exception))
                .rawPayload(record != null ? record.value() : null)
                .status(CdcFailureStatus.FAILED)
                .kafkaOffset(record != null ? record.offset() : null)
                .kafkaPartition(record != null ? record.partition() : null)
                .tsMs(event != null ? event.getTsMs() : null)
                .build();

        cdcFailureLogRepository.save(log);
        return log;
    }

    // log a failure without a Kafka record (e.g. leave balance)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CdcFailureLog logFailure(
            EmployeeCdcEvent event,
            FailureType failureType,
            Exception exception) {
        return logFailure(null, event, failureType, exception);
    }

    // mark a log as resolved after successful retry
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResolved(CdcFailureLog failureLog) {
        failureLog.setStatus(CdcFailureStatus.RESOLVED);
        failureLog.setResolvedAt(LocalDateTime.now());
        cdcFailureLogRepository.save(failureLog);
        log.info("CDC failure resolved: id={} employee={}",
                failureLog.getId(), failureLog.getEmployeeId());
    }

    // increment retry count
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetrying(CdcFailureLog failureLog) {
        failureLog.setStatus(CdcFailureStatus.RETRYING);
        failureLog.setRetryCount(failureLog.getRetryCount() + 1);
        failureLog.setLastRetriedAt(LocalDateTime.now());
        cdcFailureLogRepository.save(failureLog);
    }

    // mark as exhausted after max retries
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExhausted(CdcFailureLog failureLog, Exception e) {
        failureLog.setStatus(CdcFailureStatus.EXHAUSTED);
        failureLog.setErrorMessage(e.getMessage());
        failureLog.setStackTrace(getStackTrace(e));
        failureLog.setLastRetriedAt(LocalDateTime.now());
        cdcFailureLogRepository.save(failureLog);
        log.error("CDC failure exhausted after {} retries: id={} employee={}",
                failureLog.getRetryCount(), failureLog.getId(), failureLog.getEmployeeId());
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        // truncate to 4000 chars to avoid DB column overflow
        return trace.length() > 4000 ? trace.substring(0, 4000) + "..." : trace;
    }
}