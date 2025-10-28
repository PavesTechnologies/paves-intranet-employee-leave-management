package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface ApprovalStageRepository extends JpaRepository<ApprovalStage, UUID> {
    List<ApprovalStage> findByRequestIdOrderByLevelAsc(UUID requestId);
    List<ApprovalStage> findByApproverIdAndStatus(String approverId, String status);

    List<ApprovalStage> findByRequestIdAndStatusIn(UUID id, List<String> pending);
    List<ApprovalStage> findByRequestIdAndStatus(UUID requestId, String status);
    List<ApprovalStage> findByRequestIdAndApproverId(UUID requestId, String approverId);
    Optional<ApprovalStage> findByRequestIdAndApproverIdAndStatus(
            UUID requestId, String approverId, String status
    );

    List<ApprovalStage> findByRequestIdAndLevel(UUID requestId, Integer Level);

    List<ApprovalStage> findByRequestId(UUID requestId);

}