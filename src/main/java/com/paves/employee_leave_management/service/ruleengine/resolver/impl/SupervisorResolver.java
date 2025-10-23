package com.paves.employee_leave_management.service.ruleengine.resolver.impl;

import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.ApprovalStep;
import com.paves.employee_leave_management.entities.Employee; // Changed
import com.paves.employee_leave_management.enums.ApproverType;
import com.paves.employee_leave_management.repo.EmployeeRepo; // Changed
import com.paves.employee_leave_management.service.ruleengine.resolver.ApproverResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorResolver implements ApproverResolver {

    private final EmployeeRepo employeeRepository; // Changed

    @Override
    public List<String> resolve(Request request, ApprovalStep step) {
        Optional<Employee> maker = employeeRepository.findById(request.getCreatedBy()); // Changed

        if (maker.isEmpty()) {
            log.warn("Maker employee not found for request {}. Cannot resolve SUPERVISOR.", request.getId());
            return List.of();
        }

        Employee manager = maker.get().getManager();
        if (manager == null) {
            log.warn("Employee {} has no manager. Cannot resolve SUPERVISOR for request {}.",
                    maker.get().getEmployeeId(), request.getId());
            return List.of();
        }

        return List.of(manager.getEmployeeId()); // Changed
    }

    @Override
    public ApproverType getApproverType() {
        return ApproverType.SUPERVISOR;
    }
}