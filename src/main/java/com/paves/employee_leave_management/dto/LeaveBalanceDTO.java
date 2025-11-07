package com.paves.employee_leave_management.dto;


import com.paves.employee_leave_management.enums.LeaveStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceDTO {
    private String balanceId;
    private String employeeId;
    private String employeeName;
    private String leaveTypeId;
    private String leaveTypeName;
    private Double totalLeaves;
    private Double accruedLeaves;
    private Double usedLeaves;
    private Double remainingLeaves;
    private Double carriedForward;
    //    private Integer availableBalance;
    private Integer year;
    private LeaveStatus status = LeaveStatus.APPROVED;
}
