package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ApprovalStageRepository extends JpaRepository<ApprovalStage, UUID> {
    List<ApprovalStage> findByRequestIdOrderByLevelAsc(UUID requestId);
    List<ApprovalStage> findByApproverIdAndStatus(UUID approverId, String status);

    List<ApprovalStage> findByRequestIdAndStatusIn(UUID id, List<String> pending);
}