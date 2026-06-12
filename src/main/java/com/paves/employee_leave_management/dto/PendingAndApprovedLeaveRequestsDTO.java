package com.paves.employee_leave_management.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Data
@Setter
@Getter
public class PendingAndApprovedLeaveRequestsDTO {
    String empId;
    String empName;
    LocalDate startDate;
    LocalDate endDate;
    String status;

    public PendingAndApprovedLeaveRequestsDTO(String empId, String empName, LocalDate startDate, LocalDate endDate, String status) {
        this.empId = empId;
        this.empName = empName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

}
