package com.paves.employee_leave_management.dto;



import lombok.*;

import java.util.List;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmployeeLeaveBalanceForDropdown {
    private String employeeId;
    private Integer year;
    List<LeaveBalanceRemainingForLeaveDropDown> regular;
    List<LeaveBalanceRemainingForLeaveDropDown> genderBasedLeaveBalances;
}
