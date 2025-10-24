package com.paves.employee_leave_management.entities;

public enum LeaveStatus {
    PENDING,    // When leave is submitted but not yet reviewed
    APPROVED,   // When leave is approved by manager
    REJECTED,   // When leave is rejected by manager
    PENDING_APPROVAL, CANCELLED   // When leave is cancelled by employee
}