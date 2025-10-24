package com.paves.employee_leave_management.service;

// Create this DTO
// Create this DTO
import com.paves.employee_leave_management.dto.BulkActionRequestDTO;
import com.paves.employee_leave_management.dto.BulkActionResultDTO;
import com.paves.employee_leave_management.service.ruleengine.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Use Spring's Transactional

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalActionService {

    private final WorkflowEngine workflowEngine;

    /**
     * Processes multiple approval actions (Approve/Reject) in bulk.
     * It attempts each action individually within a single transaction.
     * If one action fails, others might still succeed depending on the cause.
     *
     * @param request DTO containing the list of stage IDs, approver ID, action type, and optional comment.
     * @return DTO summarizing successful and failed actions.
     */
    @Transactional // One transaction for the whole batch
    public BulkActionResultDTO processBulkActions(BulkActionRequestDTO request) {
        String approverId = request.getApproverId();
        String actionType = request.getActionType().toUpperCase(); // Ensure uppercase
        String comment = request.getComment();

        List<UUID> successfulStageIds = new ArrayList<>();
        List<BulkActionResultDTO.FailedAction> failedActions = new ArrayList<>();

        log.info("Processing bulk action '{}' for approver {} on {} stages.",
                actionType, approverId, request.getStageIds().size());

        for (UUID stageId : request.getStageIds()) {
            try {
                // Delegate the actual processing to the WorkflowEngine
                workflowEngine.processAction(stageId, approverId, actionType, comment);
                successfulStageIds.add(stageId);
                log.debug("Successfully processed action '{}' for stage {}", actionType, stageId);
            } catch (Exception e) {
                // Log the error and add it to the failure list
                log.error("Failed to process action '{}' for stage {}. Reason: {}",
                        actionType, stageId, e.getMessage());
                failedActions.add(new BulkActionResultDTO.FailedAction(stageId, e.getMessage()));
                // Continue to the next stage - do not roll back the entire batch here
                // The @Transactional annotation will handle rollback if a critical DB error occurs.
                // Business validation errors (like wrong approver) only fail that specific stage.
            }
        }

        log.info("Bulk action complete for approver {}. Success: {}, Failures: {}",
                approverId, successfulStageIds.size(), failedActions.size());

        return new BulkActionResultDTO(successfulStageIds, failedActions);
    }
}
