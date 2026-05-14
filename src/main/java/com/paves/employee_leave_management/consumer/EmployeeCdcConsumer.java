package com.paves.employee_leave_management.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.EmployeeCdcEvent;
import com.paves.employee_leave_management.entities.Employee;

import com.paves.employee_leave_management.enums.EmployeeStatus;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.service.LeaveBalanceServiceImple;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeCdcConsumer {

    private final EmployeeRepo employeeRepository;
    private final ObjectMapper objectMapper;
    private final EmployeeServiceInterface employeeService;

    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final GenderBasedLeaveBalanceServiceInterface genderBasedLeaveBalanceService;

    @KafkaListener(
            topics = "eos.eos.employee_details",
            groupId = "lms-employee-consumer"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String value = record.value();

            if (value == null) {
                log.warn("Null tombstone message, skipping");
                ack.acknowledge();
                return;
            }

            EmployeeCdcEvent event = objectMapper.readValue(value, EmployeeCdcEvent.class);

            log.info("CDC event received: op={} employee_uuid={}",
                    event.getOp(), event.getEmployeeUuid());

            if (event.getEmployeeUuid() == null) {
                log.warn("Event has no employee_uuid, skipping");
                ack.acknowledge();
                return;
            }

            boolean isDeleted = "true".equals(event.getDeleted())
                    || "d".equals(event.getOp());

            if (isDeleted) {
                handleDelete(event.getEmployeeUuid());
            } else {
                try {
                    handleUpsert(event);
                } catch (Exception e) {
                    log.error("Failed to upsert employee {} — {} — skipping this message",
                            event.getEmployeeUuid(), e.getMessage());
                    // acknowledge anyway — don't block the queue for one bad record
                }
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process CDC event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @Transactional
    public void handleUpsert(EmployeeCdcEvent event) {

        String lmsId = (event.getEmployeeId() != null && !event.getEmployeeId().isBlank())
                ? event.getEmployeeId()
                : event.getEmployeeUuid();
        // Step 1 — find or create employee
        Employee employee = employeeRepository
                .findByEmployeeUuid(event.getEmployeeUuid())
                .orElse(new Employee());



        // Step 2 — map basic fields
        employee.setEmployeeUuid(event.getEmployeeUuid());
        employee.setEmployeeId(event.getEmployeeId() != null ?
                event.getEmployeeId() : event.getEmployeeUuid());
        employee.setFirstName(safe(event.getFirstName(), "Unknown"));
        employee.setLastName(safe(event.getLastName(), "Unknown"));
        employee.setEmail(safe(event.getWorkEmail(),
                event.getEmployeeUuid() + "@placeholder.com"));
        employee.setGender(safe(event.getGender().toUpperCase(), "Other"));
        employee.setPhone(event.getContactNumber());
        employee.setJobTitle(safe(event.getDesignationUuid(), "Employee"));
        employee.setRole(safe(event.getEmploymentStatus(), "EMPLOYEE"));
        employee.setStatus(resolveStatus(event.getEmploymentStatus()));
        employee.setSalary(BigDecimal.ZERO);

        // only set password on new employees, never overwrite existing
        if (employee.getPassword() == null || employee.getPassword().isBlank()) {
            employee.setPassword("SYNC_" + UUID.randomUUID()
                    .toString().replace("-", "").substring(0, 8));
        }

        // parse joining date
        if (event.getJoiningDate() != null && !event.getJoiningDate().isBlank()) {
            try {
                // Try direct parse first
                employee.setHireDate(LocalDate.parse(event.getJoiningDate()));
            } catch (Exception e) {
                try {
                    // It's epoch days
                    employee.setHireDate(LocalDate.ofEpochDay(Long.parseLong(event.getJoiningDate())));
                } catch (Exception ex) {
                    employee.setHireDate(LocalDate.now());
                }
            }
        }
// Step 3 — link manager
        if (event.getReportingManagerUuid() != null
                && !event.getReportingManagerUuid().isBlank()) {
            employeeRepository.findById(event.getReportingManagerUuid())
                    .ifPresentOrElse(
                            manager -> {
                                employee.setManager(manager);
                                log.info("Linked manager {} to employee {}",
                                        event.getReportingManagerUuid(), lmsId);
                            },
                            () -> log.warn("Manager {} not in LMS yet, skipping for {}",
                                    event.getReportingManagerUuid(), lmsId)
                    );
        }

// Step 4 — link HR (created_by)
        if (event.getCreatedBy() != null && !event.getCreatedBy().isBlank()) {
            employeeRepository.findById(event.getCreatedBy())
                    .ifPresentOrElse(
                            hr -> {
                                employee.setHr(hr);
                                log.info("Linked HR {} to employee {}",
                                        event.getCreatedBy(), lmsId);
                            },
                            () -> log.warn("HR {} not in LMS yet, skipping for {}",
                                    event.getCreatedBy(), lmsId)
                    );
        }

        // Step 5 — save
        employeeRepository.save(employee);
        log.info("Upserted employee: {} ({})", lmsId, event.getEmployeeUuid());

        try {
            leaveBalanceService.createLeaveBalanceForNewEmployee(lmsId);
            log.info("Leave balances created for employee: {}", lmsId);
        } catch (Exception e) {
            log.error("Failed to create leave balances for employee: {} — {}",
                    lmsId, e.getMessage());
        }

        // Step 6 — after saving, try to back-fill any employees
        // who were waiting for this employee to exist as their manager/HR
        backFillDependents(event.getEmployeeUuid());
    }

    private void backFillDependents(String lmsId) {
        // nothing changes here — manager_id and hr_id in LMS
        // now point to employee_id (5100001 format)
        // so back-fill still works correctly
        employeeRepository.findByManagerIsNullAndManagerId(lmsId)
                .forEach(emp -> {
                    employeeRepository.findById(lmsId).ifPresent(manager -> {
                        emp.setManager(manager);
                        employeeRepository.save(emp);
                        log.info("Back-filled manager {} for employee {}",
                                lmsId, emp.getEmployeeId());
                    });
                });

        employeeRepository.findByHrIsNullAndHrId(lmsId)
                .forEach(emp -> {
                    employeeRepository.findById(lmsId).ifPresent(hr -> {
                        emp.setHr(hr);
                        employeeRepository.save(emp);
                        log.info("Back-filled HR {} for employee {}",
                                lmsId, emp.getEmployeeId());
                    });
                });
    }

    public void handleDelete(String employeeUuid) {
        employeeRepository.findByEmployeeUuid(employeeUuid).ifPresentOrElse(emp -> {
            try {
                genderBasedLeaveBalanceService.deleteLeaveBalance(emp.getEmployeeId());
                leaveBalanceService.deleteLeaveBalance(emp.getEmployeeId());
                log.info("Deleted leave balances for employee: {}", emp.getEmployeeId());
            } catch (Exception e) {
                log.error("Failed to delete leave balances for employee {} — {}",
                        emp.getEmployeeId(), e.getMessage());
            }
            try {
                employeeService.handleDelete(emp.getEmployeeId());
                log.info("Deleted employee: {} ({})", emp.getEmployeeId(), employeeUuid);
            } catch (Exception e) {
                log.error("Failed to delete employee {} — {}",
                        emp.getEmployeeId(), e.getMessage());
            }
        }, () -> log.warn("Delete event received but employee not found in LMS: {}", employeeUuid));
    }

    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private EmployeeStatus resolveStatus(String status){
         return  EmployeeStatus.valueOf(status.toUpperCase());
    }
}