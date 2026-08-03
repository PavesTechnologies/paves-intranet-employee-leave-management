package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalRequest;
import com.paves.employee_leave_management.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByApproverIdAndStatus(Long approverId, RequestStatus status);

    List<ApprovalRequest> findByMakerIdAndStatus(Long makerId, RequestStatus status);

    // Corrected method to traverse the relationship: find by workflowId and the approvalLevel inside the rule object
    Optional<ApprovalRequest> findByWorkflowIdAndRule_ApprovalLevel(String workflowId, int approvalLevel);

    List<ApprovalRequest> findByWorkflowIdAndStatus(String workflowId, RequestStatus status);
}
