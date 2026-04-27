package com.paves.employee_leave_management.dto;


import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class LeaveBalanceRemainingForLeaveDropDown {
    private String leaveTypeId;
    private String leaveName;
    private Double remainingLeaves;
    private boolean allowHalfDay;
}
