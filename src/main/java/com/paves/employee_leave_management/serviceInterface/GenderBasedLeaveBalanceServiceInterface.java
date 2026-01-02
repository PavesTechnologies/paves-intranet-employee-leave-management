package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;

import java.util.List;

public interface GenderBasedLeaveBalanceServiceInterface {
    void createLeaveBalanceForAllEmployees(GenderBasedLeave genderBasedLeave);
    void updateLeaveBalanceForEmployee(GenderBasedLeave genderBasedLeave, String employeeId);
    List<GenderBasedLeaveBalance> getCurrentYearBalances(String employeeId);
}
