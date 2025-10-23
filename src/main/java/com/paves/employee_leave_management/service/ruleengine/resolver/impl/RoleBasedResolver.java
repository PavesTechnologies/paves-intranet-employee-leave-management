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
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBasedResolver implements ApproverResolver {

    private final EmployeeRepo employeeRepository; // Changed

    @Override
    public List<String> resolve(Request request, ApprovalStep step) {
        String role = step.getApproverValue();
        if (!StringUtils.hasText(role)) {
            log.warn("ROLE_BASED step has no approverValue (role) defined for RuleSet {}.",
                    step.getRuleSet().getId());
            return List.of();
        }

        List<Employee> employees = employeeRepository.findByRole(role); // Changed
        if (employees.isEmpty()) {
            log.warn("No employees found with role '{}' for request {}.", role, request.getId());
            return List.of();
        }

        return employees.stream().map(Employee::getEmployeeId).collect(Collectors.toList()); // Changed
    }

    @Override
    public ApproverType getApproverType() {
        return ApproverType.ROLE_BASED;
    }
}