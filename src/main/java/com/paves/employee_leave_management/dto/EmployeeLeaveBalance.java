package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalance;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeLeaveBalance implements Serializable {
    private String employeeId;
    private Integer year;
    List<LeaveBalance> regular;
    List<GenderBasedLeaveBalance> genderBasedLeaveBalances;
}
