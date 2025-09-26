package com.paves.employee_leave_management.dto;

import compoffvalidation.ValidCompoffRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@ValidCompoffRequest
public class LeaveCompoffRequestDTO {
    @NotBlank(message = "EmployeeId is required")
    private String employeeId;
    private String managerId;
    @NotNull(message = "StartDate is required")
    private LocalDate startDate;
    @NotNull(message = "endDate is required")
    private LocalDate endDate;
    @NotNull(message = "days are required")
    private double duration;
    @NotBlank(message = "note is required")
    private String note;
//    private String file;
}
