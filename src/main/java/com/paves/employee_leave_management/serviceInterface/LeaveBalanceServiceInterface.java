package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.List;


public interface LeaveBalanceServiceInterface {
    void createLeaveBalanceForNewEmployee(String EmpId);
    void processYearEndCarryForward();


    void triggerMonthlyLeaveAccrual();

    ResponseEntity<LeaveBalance> findByBalanceId(String balanceId);
    ResponseEntity<List<LeaveBalance>> getAllLeaveBalances();
    ResponseEntity<List<LeaveBalance>>  findByEmployeeId(String employeeId);

    ResponseEntity<List<LeaveBalance>> findByLeaveId(String leaveId);
}

