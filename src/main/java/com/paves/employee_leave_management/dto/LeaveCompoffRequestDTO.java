package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveCompoffRequestDTO {
    @NotBlank(message = "EmployeeId is required")
    private String employeeId;
    private String managerId;
    @NotBlank(message = "StartDate is required")
    private LocalDate startDate;
    @NotBlank(message = "endDate is required")
    private LocalDate endDate;
    @NotBlank(message = "days are required")
    private double duration;
    @NotBlank(message = "note is required")
    private String note;
//    private String file;
}
