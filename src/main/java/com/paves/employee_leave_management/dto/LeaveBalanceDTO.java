package com.paves.employee_leave_management.dto;

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
    private Integer totalLeaves;
    private Integer accruedLeaves;
    private Integer usedLeaves;
    private Integer remainingLeaves;
    private Integer carriedForward;
//    private Integer availableBalance;
    private Integer year;
}
