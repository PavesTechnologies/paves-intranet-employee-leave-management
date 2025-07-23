package com.paves.employee_leave_management.dao;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;

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
}