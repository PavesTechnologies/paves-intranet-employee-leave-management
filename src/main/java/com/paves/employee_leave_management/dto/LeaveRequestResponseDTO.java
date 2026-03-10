package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.LeaveStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponseDTO {

    private String leaveId;

    // Employee — only what's needed for display & cancel logic
    private String employeeId;
    private String employeeFullName;

    // Leave type — resolved from either LeaveType or GenderBasedLeave
    private String leaveTypeId;
    private String leaveName;       // resolved display name e.g. "PATERNITY_LEAVE"

    // Dates
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate requestDate;

    // Sessions
    private String startSession;
    private String endSession;

    // Days & reason
    private double daysRequested;
    private String reason;
    private String driveLink;

    // Status
    private LeaveStatus status;
    private String managerComment;

    private String approvedBy;

    private Integer year;
}