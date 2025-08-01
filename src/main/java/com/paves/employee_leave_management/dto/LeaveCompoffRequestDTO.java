package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveCompoffRequestDTO {
    @NotBlank(message = "Employee Id is Required")
    private String employeeId;
//    private String managerId;
//    private LocalDate workedDate;
    @NotBlank(message = "Start Date is Required")
    private LocalDate startDate;
    @NotBlank(message = "End Date is Required")
    private LocalDate endDate;
    @NotBlank(message = "Days are Required")
    private double days;
    private String halfDays;
    @NotBlank(message = "Note is Required")
    private String note;
//    private String file;
}
