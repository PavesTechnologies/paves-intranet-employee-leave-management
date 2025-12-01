package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Data
@AllArgsConstructor
public class LeaveRequestDTO {
    private final String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private Employee approvedBy;
    private String employeeId;

    public LeaveRequestDTO(LeaveRequest leaveRequest) {
        this.startDate = leaveRequest.getStartDate();
        this.endDate = leaveRequest.getEndDate();
        this.reason = leaveRequest.getReason();
        this.status = leaveRequest.getStatus().name(); // Enum to String
        this.approvedBy = leaveRequest.getApprovedBy();
        this.employeeId = leaveRequest.getEmployee().getEmployeeId();
    }
}
