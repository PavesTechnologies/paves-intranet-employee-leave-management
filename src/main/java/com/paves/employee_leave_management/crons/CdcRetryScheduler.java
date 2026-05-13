package com.paves.employee_leave_management.crons;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.consumer.EmployeeCdcConsumer;
import com.paves.employee_leave_management.dto.EmployeeCdcEvent;
import com.paves.employee_leave_management.entities.CdcFailureLog;
import com.paves.employee_leave_management.repo.CdcFailureLogRepository;
import com.paves.employee_leave_management.service.CdcFailureLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 600000) // every 10 minutes
    public void retryFailedEvents() {
        List<CdcFailureLog> retryable = cdcFailureLogRepository.findRetryableLogs();

        if (retryable.isEmpty()) {
            log.debug("No CDC failures to retry");
            return;
        }

        log.info("Retrying {} failed CDC events", retryable.size());

        for (CdcFailureLog failure : retryable) {
            try {
                cdcFailureLogService.markRetrying(failure);

                // parse the stored raw payload back into event
                EmployeeCdcEvent event = objectMapper.readValue(
                        failure.getRawPayload(), EmployeeCdcEvent.class);

                // retry based on failure type
                switch (failure.getFailureType()) {
                    case UPSERT_FAILED -> employeeCdcConsumer.handleUpsert(event);
                    case LEAVE_BALANCE_FAILED -> {
                        // just retry leave balance
                        String lmsId = event.getEmployeeId() != null
                                ? event.getEmployeeId() : event.getEmployeeUuid();
                        // inject and call directly
                    }
                    case DELETE_FAILED -> employeeCdcConsumer.handleDelete(event.getEmployeeUuid());
                    default -> log.warn("No retry handler for type: {}", failure.getFailureType());
                }

                cdcFailureLogService.markResolved(failure);
                log.info("Successfully retried CDC failure: id={} employee={}",
                        failure.getId(), failure.getEmployeeId());

            } catch (Exception e) {
                if (failure.getRetryCount() >= failure.getMaxRetries()) {
                    cdcFailureLogService.markExhausted(failure, e);
                } else {
                    cdcFailureLogService.logFailure(null, null,
                            failure.getFailureType(), e);
                }
            }
        }
    }
}