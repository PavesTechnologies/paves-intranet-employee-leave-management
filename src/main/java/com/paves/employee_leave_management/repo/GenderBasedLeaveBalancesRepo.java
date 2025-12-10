package com.paves.employee_leave_management.repo;


import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenderBasedLeaveBalancesRepo extends JpaRepository<GenderBasedLeaveBalance, String> {

    Optional<GenderBasedLeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);
    List<GenderBasedLeaveBalance> findByEmployeeIdAndYear(String employeeId, int year);
    void deleteByLeaveTypeId(String leaveTypeId);
//    GenderBasedLeaveBalance findByEmployeeIdAndLeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);
}
