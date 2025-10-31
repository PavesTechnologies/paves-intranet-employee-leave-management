package com.paves.employee_leave_management.enums;

public enum ApproverType {
    LINE_MANAGER,
    FUNCTIONAL_APPROVER,
    ROLE_BASED,
    DIRECT_MAPPING;

    public enum LeaveStatusCompoff {
        PENDING,
        APPROVED,
        EXPIRED, CANCELLED, REJECTED
    }
}
