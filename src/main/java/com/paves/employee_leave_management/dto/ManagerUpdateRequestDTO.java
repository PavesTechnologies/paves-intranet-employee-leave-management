package com.paves.employee_leave_management.dto;

import lombok.*;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * DTO for manager update operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ManagerUpdateRequestDTO {
    
    @NotBlank(message = "Manager ID is required")
    private String managerId;
    
    @NotBlank(message = "Leave ID is required")
    private String leaveId;
    
    // Optional fields for update
    private String employeeId;
    private String managerComment;
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
