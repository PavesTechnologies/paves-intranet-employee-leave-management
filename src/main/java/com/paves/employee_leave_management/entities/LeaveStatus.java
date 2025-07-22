package com.paves.employee_leave_management.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Enum for Leave Status
@Getter
@AllArgsConstructor
public enum LeaveStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");
    private final String displayName;
}