package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * DTO for manager update operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerUpdateRequestDTO {
    
    @NotBlank(message = "Manager ID is required")
    private String managerId;
    
    @NotBlank(message = "Leave ID is required")
    private String leaveId;
    
    // Optional fields for update
    private String leaveTypeId;
    private LocalDate startDate;
    private String startSession;
    private String endSession;
    private LocalDate endDate;
    private Double daysRequested;
    private LocalDate requestDate;
    private String reason;
    private String driveLink;
}
