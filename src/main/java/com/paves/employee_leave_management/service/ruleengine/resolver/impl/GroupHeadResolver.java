package com.paves.employee_leave_management.service.ruleengine.resolver.impl;

import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.ApprovalStep;
import com.paves.employee_leave_management.entities.Group;
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
public class GroupHeadResolver implements ApproverResolver {

    private final EmployeeRepo employeeRepository; // Changed

    @Override
    public List<String> resolve(Request request, ApprovalStep step) {
        Optional<Employee> maker = employeeRepository.findById(request.getCreatedBy()); // Changed
        if (maker.isEmpty()) {
            log.warn("Maker employee not found for request {}. Cannot resolve GROUP_HEAD.", request.getId());
            return List.of();
        }

        Group group = maker.get().getGroup();
        if (group == null) {
            log.warn("Employee {} is not associated with any group. Cannot resolve GROUP_HEAD.",
                    maker.get().getEmployeeId());
            return List.of();
        }

        Employee head = group.getHead();
        if (head == null) {
            log.warn("Group {} has no head assigned. Cannot resolve GROUP_HEAD.", group.getName());
            return List.of();
        }

        return List.of(head.getEmployeeId()); // Changed
    }

    @Override
    public ApproverType getApproverType() {
        return ApproverType.GROUP_HEAD;
    }
}