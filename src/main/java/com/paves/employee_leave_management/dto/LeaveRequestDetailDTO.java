
package com.paves.employee_leave_management.dto;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.ApprovalAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime; // Import LocalDateTime
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LeaveRequestDetailDTO {
    private UUID stageId;
    private UUID requestId;
    // Leave Request Fields
    private String leaveId;
    private String employeeId;
    private String employeeName;
    private String leaveTypeName;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private double daysRequested;
    private String reason;
    private String driveLink;
    private String startSession;
    private String endSession;
    private String currentWorkflowStatus;
    private java.time.LocalDateTime requestedAt;

    // --- New Fields ---
    private String lastActionById; // Employee ID of the last actor
    private String lastActionByName; // Name of the last actor
    private LocalDateTime lastActionAt; // Time of the last action
    private String lastActionType; // APPROVE / REJECT
    // --- End New Fields ---

    // Approval History (Optional)
    private List<ApprovalAction> history;
}