package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalance;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeLeaveBalance {
    List<LeaveBalance> regular;
    List<GenderBasedLeaveBalance> genderBasedLeaveBalances;
}
