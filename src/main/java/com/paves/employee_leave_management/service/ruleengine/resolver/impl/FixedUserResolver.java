package com.paves.employee_leave_management.service.ruleengine.resolver.impl;

import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.ApprovalStep;
import com.paves.employee_leave_management.enums.ApproverType;
import com.paves.employee_leave_management.service.ruleengine.resolver.ApproverResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixedUserResolver implements ApproverResolver {

    @Override
    public List<String> resolve(Request request, ApprovalStep step) {
        String employeeId = step.getApproverValue(); // Changed
        if (!StringUtils.hasText(employeeId)) {
            log.warn("FIXED_USER step has no approverValue (employeeId) defined for RuleSet {}.",
                    step.getRuleSet().getId());
            return List.of();
        }
        // Assuming the employeeId is stored directly as a String
        return List.of(employeeId);
    }

    @Override
    public ApproverType getApproverType() {
        return ApproverType.FIXED_USER;
    }
}