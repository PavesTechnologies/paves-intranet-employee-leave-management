package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.LeaveStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Integer year;
    private Integer month;
}
