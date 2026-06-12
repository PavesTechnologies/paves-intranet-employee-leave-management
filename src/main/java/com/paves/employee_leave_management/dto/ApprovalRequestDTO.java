package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for approval operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestDTO {

    @NotBlank(message = "Manager ID is required")
    private String managerId;

    @NotBlank(message = "Leave ID is required")
    private String leaveId;

    private int year;

    // Optional comment for approval
    private String comment;
}
