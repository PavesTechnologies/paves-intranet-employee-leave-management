package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 
 */
public interface LeaveBalanceServiceInterface {
    LeaveBalanceDTO getLeaveBalance(String employeeId, String leaveTypeId, Integer year);

    void createLeaveBalanceForNewEmployee(String EmpId);
    void processYearEndCarryForward();

    void triggerMonthlyLeaveAccrual();

    ResponseEntity<LeaveBalance> findByBalanceId(String balanceId);
    ResponseEntity<List<LeaveBalance>> getAllLeaveBalances();
    ResponseEntity<List<LeaveBalance>>  findByEmployeeId(String employeeId);

    public List<LeaveBalance> getCurrentYearBalances(String employeeId);

    ResponseEntity<List<LeaveBalance>> findByLeaveId(String leaveId);

    @Transactional
    void updateLeaveBalanceAfterApproval(String employeeId, String leaveTypeId, double approvedDays, int year);

    @Transactional
    void updateLeaveBalanceAfterRejected(String employeeId, String leaveTypeId, double daysRequested, int year);

    ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(List<LeaveBalance> leaveBalance);

    ResponseEntity<String> updateLeaveBalancesFromHr(LeaveBalanceUpdateRequest request);

    void createLeaveBalanceForAllEmployees(LeaveType leaveType);

    List<String> autocomplete(String query);

    List<LeaveBalance> searchLeaveBalances(String query);
    List<String> autocompleteEmployee(String query);
}

