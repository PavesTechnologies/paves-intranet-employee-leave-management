package com.paves.employee_leave_management.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

public enum ActionType {
    CREATE_LEAVE_TYPE,
    UPDATE_LEAVE_TYPE,
    DEACTIVATE_LEAVE_TYPE,
    UPDATE_EMPLOYEE_LEAVE_BALANCE;

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
}
