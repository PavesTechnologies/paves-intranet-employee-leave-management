package com.paves.employee_leave_management.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveCompoffRequestDTO {
    private String employeeId;
    private String managerId;
    private LocalDate workedDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private double days;
    private String halfDays;
    private String note;
    private String file;
}
