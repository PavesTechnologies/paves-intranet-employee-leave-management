package com.paves.employee_leave_management.dto;

import lombok.*;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * DTO for approver-initiated leave request updates.
 * Approvers (managers/HR) can update certain fields during the approval process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproverUpdateRequestDTO {
    
    @NotNull(message = "Approver ID is required")
    private String approverId; // Employee ID of the approver making the update
    
    @NotNull(message = "Leave ID is required")
    private String leaveId;
    
    // Optional fields - only updated if provided
    private String leaveTypeId;
    
    private LocalDate startDate;
    
    private LocalDate endDate;
    
    @Min(value = 0, message = "Days requested cannot be negative")
    private Double daysRequested;
    
    private String reason;
    
    private String driveLink;
    
    private String startSession; // FULL_DAY, FIRST_HALF, SECOND_HALF
    
    private String endSession;
    
    /**
     * Reason for the update by approver.
     * This will be logged and shown in audit trail.
     */
    @NotBlank(message = "Update reason is required for approver-initiated updates")
    private String updateReason;
    
    /**
     * Whether to notify the employee of the changes.
     * Default: true
     */
    @Builder.Default
    private Boolean notifyEmployee = true;

    private Boolean restartWorkflow;
}
