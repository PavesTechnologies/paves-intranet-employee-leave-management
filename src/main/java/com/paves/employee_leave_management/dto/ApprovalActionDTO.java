package com.paves.employee_leave_management.dto;

import lombok.Data;

@Data
public class ApprovalActionDTO {
    private String approverId; // The ID of the employee who is approving/rejecting
    private String decision; // "APPROVE" or "REJECT"
}
