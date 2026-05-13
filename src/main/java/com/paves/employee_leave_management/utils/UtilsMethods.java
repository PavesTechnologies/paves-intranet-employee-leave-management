package com.paves.employee_leave_management.utils;

import com.paves.employee_leave_management.enums.LeaveTypesEnum;

public class UtilsMethods {
    public static String resolveLeaveLabel(String rawName) {
        if (rawName == null) return null;
        try {
            return LeaveTypesEnum.valueOf(rawName).getLabel(); // e.g. "PATERNITY_LEAVE" → "Paternity Leave"
        } catch (IllegalArgumentException e) {
            return rawName; // fallback to raw name if not found in enum
        }
    }
}
