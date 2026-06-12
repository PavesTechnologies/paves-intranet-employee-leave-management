package com.paves.employee_leave_management.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ApproverType {
    LINE_MANAGER,
    FUNCTIONAL_APPROVER,
    ROLE_BASED,
    DIRECT_MAPPING
}
