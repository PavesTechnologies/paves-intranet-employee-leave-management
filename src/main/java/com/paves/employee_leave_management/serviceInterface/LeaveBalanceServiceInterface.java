package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;

import java.time.LocalDate;
import java.util.List;


public interface LeaveBalanceServiceInterface {
    void createLeaveBalanceForNewEmployee(String EmpId);
    void processYearEndCarryForward();
    LeaveBalance findByBalanceId(String balanceId);
    List<LeaveBalance> getAllLeaveBalances();
    List<LeaveBalance> findByEmployeeId(String employeeId);
    List<LeaveBalance> findByLeaveName(String leaveName);
}

