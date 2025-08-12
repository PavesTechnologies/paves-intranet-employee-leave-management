package com.paves.employee_leave_management.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
