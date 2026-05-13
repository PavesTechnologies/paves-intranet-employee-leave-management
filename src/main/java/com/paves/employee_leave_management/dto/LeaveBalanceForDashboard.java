package com.paves.employee_leave_management.dto;


import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Data
public class LeaveBalanceForDashboard {
    private String employeeId;
    private String leaveName;
    private Double remainingBalance;
    private Double totalBalance;
}
