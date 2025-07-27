package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.LeaveStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * DTO for manager query operations with filtering capabilities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerQueryDTO {
    
    @NotBlank(message = "Manager ID is required")
    private String managerId;
    
    // Optional filters
    private LeaveStatus status;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String employeeId;
    private String leaveTypeId;
}
