package com.paves.employee_leave_management.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalanceUpdateHandleDTO {
    private String leaveTypeName;
    private Double remainingLeaves;
}
