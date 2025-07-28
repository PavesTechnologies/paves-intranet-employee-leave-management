package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for rejection operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectionRequestDTO {
    
    @NotBlank(message = "Manager ID is required")
    private String managerId;
    
    @NotBlank(message = "Leave ID is required")
    private String leaveId;
    
    @NotBlank(message = "Comment is required for rejection")
    private String comment;
}
