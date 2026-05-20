package com.paves.employee_leave_management.crons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.consumer.EmployeeCdcConsumer;
import com.paves.employee_leave_management.dto.EmployeeCdcEvent;
import com.paves.employee_leave_management.entities.CdcFailureLog;
import com.paves.employee_leave_management.repo.CdcFailureLogRepository;
import com.paves.employee_leave_management.service.CdcFailureLogService;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdcRetryScheduler {

    private final CdcFailureLogRepository cdcFailureLogRepository;
    private final CdcFailureLogService cdcFailureLogService;
    private final EmployeeCdcConsumer employeeCdcConsumer;
    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 600000)
    @SchedulerLock(
            name = "Cdc_Retry_Job",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT15M"
    )
    public void retryFailedEvents() {
        List<CdcFailureLog> retryable = cdcFailureLogRepository.findRetryableLogs();

        if (retryable.isEmpty()) {
            log.debug("No CDC failures to retry");
            return;
        }

        log.info("Retrying {} failed CDC events", retryable.size());

        for (CdcFailureLog failure : retryable) {
            retrySingleEvent(failure);
        }
    }

    private void retrySingleEvent(CdcFailureLog failure) {
        try {
            cdcFailureLogService.markRetrying(failure);

            EmployeeCdcEvent event = objectMapper.readValue(
                    failure.getRawPayload(), EmployeeCdcEvent.class);

            switch (failure.getFailureType()) {
                case UPSERT_FAILED -> {
                    employeeCdcConsumer.handleUpsert(event);
                }
                case LEAVE_BALANCE_FAILED -> {
                    String lmsId = (event.getEmployeeId() != null
                            && !event.getEmployeeId().isBlank())
                            ? event.getEmployeeId()
                            : event.getEmployeeUuid();
                    leaveBalanceService.createLeaveBalanceForNewEmployee(lmsId);
                    log.info("Retried leave balance for employee: {}", lmsId);
                }
                case DELETE_FAILED -> {
                    employeeCdcConsumer.handleDelete(event.getEmployeeUuid());
                }
                default -> {
                    log.warn("No retry handler for failure type: {}", failure.getFailureType());
                    cdcFailureLogService.markExhausted(failure,
                            new UnsupportedOperationException(
                                    "No handler for type: " + failure.getFailureType()));
                    return;
                }
            }

            cdcFailureLogService.markResolved(failure);
            log.info("Successfully retried CDC failure: id={} employee={}",
                    failure.getId(), failure.getEmployeeId());

        } catch (JsonProcessingException e) {
            log.error("Malformed CDC payload for id={}: {}", failure.getId(), e.getMessage());
            cdcFailureLogService.markExhausted(failure, e);

        } catch (Exception e) {
            log.error("Retry failed for id={} employee={}: {}",
                    failure.getId(), failure.getEmployeeId(), e.getMessage());
            if (failure.getRetryCount() >= failure.getMaxRetries()) {
                cdcFailureLogService.markExhausted(failure, e);
            }
        }
    }
}