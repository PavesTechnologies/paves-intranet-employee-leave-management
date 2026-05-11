package com.paves.employee_leave_management.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.EmployeeCdcEvent;
import com.paves.employee_leave_management.entities.Employee;

import com.paves.employee_leave_management.repo.EmployeeRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
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

    @KafkaListener(
            topics = "eos.eos_v1.employee_details",
            groupId = "lms-employee-consumer"
    )
    @Transactional
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
                handleUpsert(event);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process CDC event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    private void handleUpsert(EmployeeCdcEvent event) {
        // Step 1 — find or create employee
        Employee employee = employeeRepository
                .findById(event.getEmployeeUuid())
                .orElse(new Employee());

        // Step 2 — map basic fields
        employee.setEmployeeId(event.getEmployeeId() != null ?
                event.getEmployeeId() : event.getEmployeeUuid());
        employee.setFirstName(safe(event.getFirstName(), "Unknown"));
        employee.setLastName(safe(event.getLastName(), "Unknown"));
        employee.setEmail(safe(event.getWorkEmail(),
                event.getEmployeeUuid() + "@placeholder.com"));
        employee.setGender(safe(event.getGender(), "Other"));
        employee.setPhone(event.getContactNumber());
        employee.setJobTitle(safe(event.getDesignationUuid(), "Employee"));
        employee.setRole(safe(event.getEmploymentStatus(), "EMPLOYEE"));
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

        // Step 3 — link manager if exists in LMS already
        if (event.getReportingManagerUuid() != null) {
            Optional<Employee> manager = employeeRepository
                    .findById(event.getReportingManagerUuid());
            if (manager.isPresent()) {
                employee.setManager(manager.get());
                log.info("Linked manager {} to employee {}",
                        event.getReportingManagerUuid(), event.getEmployeeUuid());
            } else {
                log.warn("Manager {} not in LMS yet, skipping manager link for {}",
                        event.getReportingManagerUuid(), event.getEmployeeUuid());
            }
        }

        // Step 4 — link HR (created_by) if exists in LMS already
        if (event.getCreatedBy() != null) {
            Optional<Employee> hr = employeeRepository
                    .findById(event.getCreatedBy());
            if (hr.isPresent()) {
                employee.setHr(hr.get());
                log.info("Linked HR {} to employee {}",
                        event.getCreatedBy(), event.getEmployeeUuid());
            } else {
                log.warn("HR {} not in LMS yet, skipping HR link for {}",
                        event.getCreatedBy(), event.getEmployeeUuid());
            }
        }

        // Step 5 — save
        employeeRepository.save(employee);
        log.info("Upserted employee: {}", event.getEmployeeUuid());

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

    private void handleDelete(String employeeUuid) {
        employeeRepository.findById(employeeUuid).ifPresent(emp -> {
            employeeRepository.delete(emp);
            log.info("Deleted employee: {}", employeeUuid);
        });
    }

    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}