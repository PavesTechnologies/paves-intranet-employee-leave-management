package com.paves.employee_leave_management.dao;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class LeaveBalanceDAOImple implements LeaveBalanceDAO {

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

    @Override
    public void save(LeaveBalance balance) {
        leaveBalanceRepo.save(balance);
    }

    @Override
    public boolean existsByEmployeeIdAndLeaveTypeIdAndYear(String empId, String leaveTypeId, int year) {
        return false;
    }


}
