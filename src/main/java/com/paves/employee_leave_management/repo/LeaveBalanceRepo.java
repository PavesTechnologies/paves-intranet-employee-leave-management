package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveBalanceRepo extends JpaRepository<LeaveBalance, String> {

    boolean existsByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(String empId, String leaveTypeId, int year);
}
