package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;

import java.time.LocalDate;

public interface LeaveBalanceServiceInterface {
    void createLeaveBalanceForNewEmployee(Employee employee);
}

