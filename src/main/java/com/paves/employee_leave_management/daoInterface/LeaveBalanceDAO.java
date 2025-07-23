package com.paves.employee_leave_management.daoInterface;

import com.paves.employee_leave_management.entities.LeaveBalance;

public interface LeaveBalanceDAO {
    void save(LeaveBalance balance);
    boolean existsByEmployeeIdAndLeaveTypeIdAndYear(String empId, String leaveTypeId, int year);
    LeaveBalance findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear( String employeeId, String leaveTypeId, int year);
    }
