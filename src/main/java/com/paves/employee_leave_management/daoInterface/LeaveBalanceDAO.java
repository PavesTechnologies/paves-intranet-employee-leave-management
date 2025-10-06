package com.paves.employee_leave_management.daoInterface;

import com.paves.employee_leave_management.entities.LeaveBalance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceDAO {
    void save(LeaveBalance balance);
    boolean existsByEmployeeIdAndLeaveTypeIdAndYear(String empId, String leaveTypeId, int year);

    LeaveBalance findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear( String employeeId, String leaveTypeId, int year);

    LeaveBalance findById(String balanceId);
    List<LeaveBalance> findAll();
    List<LeaveBalance> findByEmployeeId(String employeeId);
    List<LeaveBalance> findByLeaveId(String leaveId);

    List<LeaveBalance> findByEmployeeIdAndYear(String employeeId, int currentYear);
}

