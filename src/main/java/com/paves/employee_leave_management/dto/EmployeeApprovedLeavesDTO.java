package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
public class EmployeeApprovedLeavesDTO {
    
    private String employeeId;
    private String employeeName;
    private List<LocalDate> approvedLeaveDates;
    
    public EmployeeApprovedLeavesDTO(String employeeId, List<LocalDate> approvedLeaveDates) {
        this.employeeId = employeeId;
        this.approvedLeaveDates = approvedLeaveDates;
    }
    
    public EmployeeApprovedLeavesDTO(String employeeId, String employeeName, List<LocalDate> approvedLeaveDates) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.approvedLeaveDates = approvedLeaveDates;
    }
}
