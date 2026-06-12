package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.enums.LeaveRevokeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRevokeRepo extends JpaRepository<LeaveRevoke, String> {
//    LeaveRevoke findByLeaveRequestId(String leaveRequestId);
    List<LeaveRevoke> findByLeaveRequestId(String leaveRequestId);

    List<LeaveRevoke> findByManagerIdAndStatus(String managerId, LeaveRevokeStatus status);


//    @Override
//    Optional<LeaveRevoke> findById(String leaveId);
}
