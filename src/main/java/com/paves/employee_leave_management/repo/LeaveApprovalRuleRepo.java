package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveApprovalRuleRepo extends JpaRepository<LeaveApprovalRule, Long> {
    List<LeaveApprovalRule> findByLeaveTypeIdAndLevel(Long leaveTypeId, int level);
    List<LeaveApprovalRule> findByLevel(int level);
}
