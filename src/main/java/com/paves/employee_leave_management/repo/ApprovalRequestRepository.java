package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalRequest;
import com.paves.employee_leave_management.enums.RequestStatus;
import com.paves.employee_leave_management.entities.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByApproverIdAndStatus(Long approverId, RequestStatus status);

    List<ApprovalRequest> findByRuleAndStatus(ApprovalRule rule, RequestStatus status);
}
