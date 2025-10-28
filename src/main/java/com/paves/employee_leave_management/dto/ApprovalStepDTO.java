package com.paves.employee_leave_management.dto;


import com.paves.employee_leave_management.enums.ApprovalMode;
import com.paves.employee_leave_management.enums.ApproverType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor // Useful for the mappers
public class ApprovalStepDTO {
    private UUID id;
    private Integer level;          // e.g., 1, 2, 3
    private ApproverType approverType; // Enum: SUPERVISOR, ROLE_BASED, etc.
    private String approverValue;  // e.g., "HR_MANAGER", "PAVEMP123", null
    private ApprovalMode approvalMode;   // Enum: SEQUENTIAL, PARALLEL
    // Optional: Add escalation fields here if you manage them via DTO
    // private Integer escalationAfterHours;
    // private ApproverType escalationApproverType;
    // private String escalationApproverValue;
}
