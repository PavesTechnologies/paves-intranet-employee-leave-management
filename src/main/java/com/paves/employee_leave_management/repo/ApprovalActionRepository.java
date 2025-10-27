package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {
    List<ApprovalAction> findByStageId(UUID stageId);
//    / REMINDER: Ensure ApprovalActionRepository has:
     Optional<ApprovalAction> findTopByStage_RequestIdOrderByActionAtDesc(UUID requestId);
     List<ApprovalAction> findByStage_RequestIdOrderByActionAtAsc(UUID requestId); // For history
}