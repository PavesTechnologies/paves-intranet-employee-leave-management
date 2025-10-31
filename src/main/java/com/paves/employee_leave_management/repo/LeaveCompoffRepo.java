package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.enums.ApproverType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveCompoffRepo extends JpaRepository<LeaveCompoff,Long> {
    List<LeaveCompoff> findByEmployeeId(String employeeId);

    List<LeaveCompoff> findByManagerIdAndStatus(String managerId, ApproverType.LeaveStatusCompoff status);

    List<LeaveCompoff> findByStatus(ApproverType.LeaveStatusCompoff leaveStatusCompoff);

    List<LeaveCompoff> findByManagerIdAndStatusOrderByWorkedDateDesc(String managerId, ApproverType.LeaveStatusCompoff status);

}
