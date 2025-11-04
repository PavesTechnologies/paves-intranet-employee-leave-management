package com.paves.employee_leave_management.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

public enum ApproverType {
    LINE_MANAGER,
    FUNCTIONAL_APPROVER,
    ROLE_BASED,
    DIRECT_MAPPING;

    @Getter
    @AllArgsConstructor
    public enum LeaveTypesEnum {
            EARNED_LEAVE("Earned Leave"),
            UNPAID_LEAVE("Unpaid Leave"),
            SICK_LEAVE("Sick Leave"),
            PATERNITY_LEAVE("Paternity Leave"),
            MATERNITY_LEAVE("Maternity Leave"),
            COMPENSATORY_LEAVE("Compensatory Leave");

            private final String label;
    }

    public enum LeaveStatus {
        PENDING,    // When leave is submitted but not yet reviewed
        APPROVED,   // When leave is approved by manager
        REJECTED,   // When leave is rejected by manager
        CANCELLED   // When leave is cancelled by employee
    }

    public enum HolidayType {
        NATIONAL,
        REGIONAL,
        OPTIONAL
    }
}
