package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveRevoke;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveRevokeRepo extends JpaRepository<LeaveRevoke, String> {
    LeaveRevoke findByLeaveRequestId(String leaveRequestId);
}
