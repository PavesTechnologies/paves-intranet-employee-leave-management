package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalRequest;
import com.paves.employee_leave_management.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findByApproverIdAndStatus(Long approverId, RequestStatus status);

    List<ApprovalRequest> findByRule_IdAndStatus(Long ruleId, RequestStatus status);
}
