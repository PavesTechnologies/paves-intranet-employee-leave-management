package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestUpdateDTO {
    private String leaveId;
    private String employeeId;
    private String leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private double daysRequested;
    private String reason;
}
