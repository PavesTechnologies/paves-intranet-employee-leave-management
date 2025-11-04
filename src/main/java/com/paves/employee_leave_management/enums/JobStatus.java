package com.paves.employee_leave_management.enums;

public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    SKIPPED;

    public enum LeaveStatusCompoff {
        PENDING,
        APPROVED,
        EXPIRED, CANCELLED, REJECTED
    }
}
