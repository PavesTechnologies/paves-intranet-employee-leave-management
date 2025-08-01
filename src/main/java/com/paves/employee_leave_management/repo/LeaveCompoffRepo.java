package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveStatusCompoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveCompoffRepo extends JpaRepository<LeaveCompoff,Long> {
    List<LeaveCompoff> findByEmployeeId(String employeeId);

    List<LeaveCompoff> findByManagerIdAndStatus(String managerId, LeaveStatusCompoff status);

    List<LeaveCompoff> findByStatus(LeaveStatusCompoff leaveStatusCompoff);

    List<LeaveCompoff> findByManagerIdAndStatusOrderByWorkedDateDesc(String managerId, LeaveStatusCompoff status);
}
