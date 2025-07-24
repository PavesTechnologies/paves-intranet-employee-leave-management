package com.paves.employee_leave_management.dao;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class LeaveBalanceDAOImple implements LeaveBalanceDAO {

    private final LeaveBalanceRepo leaveBalanceRepo;

    @Override
    public void save(LeaveBalance balance) {
        leaveBalanceRepo.save(balance);
    }

    @Override
    public boolean existsByEmployeeIdAndLeaveTypeIdAndYear(String empId, String leaveTypeId, int year) {
        return leaveBalanceRepo.existsByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(empId, leaveTypeId, year);
    }

    @Override
    public LeaveBalance findById(String balanceId) {
        return leaveBalanceRepo.findById(balanceId).orElse(null);
    }

    @Override
    public List<LeaveBalance> findAll() {
        return leaveBalanceRepo.findAll();
    }

    @Override
    public LeaveBalance findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(String employeeId,String leaveTypeId, int year) {
        return leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
    }
    @Override
    public List<LeaveBalance> findByEmployeeId(String employeeId) {
        return leaveBalanceRepo.findByEmployeeEmployeeId(employeeId);
    }

    @Override
    public List<LeaveBalance> findByLeaveId(String leaveId) {
        return leaveBalanceRepo.findByLeaveTypeLeaveTypeId(leaveId);
    }
}
