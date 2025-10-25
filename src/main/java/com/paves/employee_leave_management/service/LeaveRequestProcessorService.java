package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.entities.LeaveStatus;
import com.paves.employee_leave_management.repo.ApprovalActionRepository;
import com.paves.employee_leave_management.repo.ApprovalStageRepository;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Service responsible for finalizing leave requests after workflow completion.
 * Triggered by WorkflowCompletionListener when a leave workflow reaches APPROVED/REJECTED/CANCELLED status.
 * 
 * Key Responsibilities:
 * - Update LeaveRequest status to match workflow outcome
 * - Set approval/rejection metadata (approvedBy, responseDate, comments)
 * - Reverse balance for rejected/cancelled requests
 * - Maintain audit trail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveRequestProcessorService {

    private final LeaveRequestRepo leaveRequestRepo;
    private final ApprovalStageRepository approvalStageRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final EmployeeRepo employeeRepo;
    private final LeaveBalanceServiceInterface leaveBalanceService;

    /**
     * Processes an approved leave request workflow.
     * Updates the LeaveRequest status to APPROVED and sets approval metadata.
     * Balance is NOT touched as it was already deducted on submission.
     * 
     * @param approvedRequest The workflow Request object marked as APPROVED
     */
    @Transactional
    public void processApproved(Request approvedRequest) {
        log.info("Processing APPROVED workflow {} for Leave Request {}", 
            approvedRequest.getId(), approvedRequest.getTargetEntityId());

        // Validate input
        if (!"APPROVED".equals(approvedRequest.getStatus()) || !"LEAVE".equals(approvedRequest.getRequestType())) {
            log.warn("LeaveRequestProcessor received unexpected request (ID: {}, Status: {}, Type: {}). Skipping.",
                    approvedRequest.getId(), approvedRequest.getStatus(), approvedRequest.getRequestType());
            return;
        }

        // 1. Find the LeaveRequest entity
        LeaveRequest leaveRequest = leaveRequestRepo.findById(approvedRequest.getTargetEntityId())
                .orElseThrow(() -> {
                    log.error("CRITICAL: LeaveRequest {} not found for approved workflow {}. Data integrity issue!",
                            approvedRequest.getTargetEntityId(), approvedRequest.getId());
                    return new RuntimeException("LeaveRequest not found: " + approvedRequest.getTargetEntityId());
                });

        // 2. Defensive check - skip if already processed
        if (leaveRequest.getStatus() == LeaveStatus.APPROVED) {
            log.warn("LeaveRequest {} already processed as APPROVED. Skipping duplicate processing for workflow {}.",
                    leaveRequest.getLeaveId(), approvedRequest.getId());
            return;
        }

        // 3. Find the final approver (last person who approved)
        List<ApprovalStage> approvedStages = approvalStageRepository.findByRequestIdOrderByLevelAsc(
                approvedRequest.getId());
        
        ApprovalStage finalApprovalStage = approvedStages.stream()
                .filter(stage -> "APPROVED".equals(stage.getStatus()))
                .max(Comparator.comparing(ApprovalStage::getLevel))
                .orElse(null);

        // 4. Update LeaveRequest with approval details
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequest.setResponseDate(LocalDate.now());
        
        if (finalApprovalStage != null) {
            // Find the Employee who approved
            Employee approver = employeeRepo.findById(finalApprovalStage.getApproverId())
                    .orElse(null);
            
            if (approver != null) {
                leaveRequest.setApprovedBy(approver);
                log.info("LeaveRequest {} approved by {} at Level {}", 
                    leaveRequest.getLeaveId(), approver.getEmployeeId(), finalApprovalStage.getLevel());
            } else {
                log.warn("Approver {} not found in Employee table for LeaveRequest {}",
                        finalApprovalStage.getApproverId(), leaveRequest.getLeaveId());
            }
            
            // Get approval comment if any
            List<ApprovalAction> actions = approvalActionRepository.findByStageId(finalApprovalStage.getId());
            actions.stream()
                    .filter(action -> "APPROVE".equalsIgnoreCase(action.getActionType()))
                    .findFirst()
                    .ifPresent(action -> {
                        if (action.getComment() != null && !action.getComment().trim().isEmpty()) {
                            leaveRequest.setManagerComment(action.getComment());
                        }
                    });
        }

        // 5. Save updated LeaveRequest
        leaveRequestRepo.save(leaveRequest);

        log.info("Successfully processed APPROVED LeaveRequest {}. Status updated, balance unchanged (already deducted).",
                leaveRequest.getLeaveId());

        // TODO: Send approval notification email (deferred for now)
    }

    /**
     * Processes a rejected or cancelled leave request workflow.
     * Updates the LeaveRequest status and REVERSES the balance deduction.
     * 
     * @param rejectedRequest The workflow Request object marked as REJECTED or CANCELLED
     */
    @Transactional
    public void processRejectedOrCancelled(Request rejectedRequest) {
        String finalStatus = rejectedRequest.getStatus(); // REJECTED or CANCELLED
        log.info("Processing {} workflow {} for Leave Request {}", 
            finalStatus, rejectedRequest.getId(), rejectedRequest.getTargetEntityId());

        // Validate input
        if ((!"REJECTED".equals(finalStatus) && !"CANCELLED".equals(finalStatus)) 
            || !"LEAVE".equals(rejectedRequest.getRequestType())) {
            log.warn("LeaveRequestProcessor received unexpected request (ID: {}, Status: {}, Type: {}). Skipping.",
                    rejectedRequest.getId(), rejectedRequest.getStatus(), rejectedRequest.getRequestType());
            return;
        }

        // 1. Find the LeaveRequest entity
        LeaveRequest leaveRequest = leaveRequestRepo.findById(rejectedRequest.getTargetEntityId())
                .orElseThrow(() -> {
                    log.error("CRITICAL: LeaveRequest {} not found for {} workflow {}. Data integrity issue!",
                            rejectedRequest.getTargetEntityId(), finalStatus, rejectedRequest.getId());
                    return new RuntimeException("LeaveRequest not found: " + rejectedRequest.getTargetEntityId());
                });

        // 2. Defensive check - skip if already processed
        if (leaveRequest.getStatus() == LeaveStatus.REJECTED || leaveRequest.getStatus() == LeaveStatus.CANCELLED) {
            log.warn("LeaveRequest {} already processed as {}. Skipping duplicate processing for workflow {}.",
                    leaveRequest.getLeaveId(), leaveRequest.getStatus(), rejectedRequest.getId());
            return;
        }

        // 3. Find who rejected (or who had the pending stage when cancelled)
        List<ApprovalStage> stages = approvalStageRepository.findByRequestIdOrderByLevelAsc(
                rejectedRequest.getId());
        
        ApprovalStage rejectionStage = stages.stream()
                .filter(stage -> "REJECTED".equals(stage.getStatus()))
                .findFirst()
                .or(() -> stages.stream()
                        .filter(stage -> "CANCELLED".equals(stage.getStatus()))
                        .findFirst())
                .orElse(null);

        // 4. Update LeaveRequest with rejection/cancellation details
        LeaveStatus newStatus = "CANCELLED".equals(finalStatus) ? LeaveStatus.CANCELLED : LeaveStatus.REJECTED;
        leaveRequest.setStatus(newStatus);
        leaveRequest.setResponseDate(LocalDate.now());
        
        if (rejectionStage != null) {
            // Find the Employee who rejected
            Employee rejector = employeeRepo.findById(rejectionStage.getApproverId())
                    .orElse(null);
            
            if (rejector != null) {
                leaveRequest.setApprovedBy(rejector); // Actually "rejectedBy" but using same field
                log.info("LeaveRequest {} {} by {} at Level {}", 
                    leaveRequest.getLeaveId(), finalStatus, rejector.getEmployeeId(), rejectionStage.getLevel());
            } else {
                log.warn("Rejector {} not found in Employee table for LeaveRequest {}",
                        rejectionStage.getApproverId(), leaveRequest.getLeaveId());
            }
            
            // Get rejection comment from ApprovalAction
            List<ApprovalAction> actions = approvalActionRepository.findByStageId(rejectionStage.getId());
            actions.stream()
                    .filter(action -> "REJECT".equalsIgnoreCase(action.getActionType()) 
                                   || "CANCEL".equalsIgnoreCase(action.getActionType()))
                    .findFirst()
                    .ifPresent(action -> {
                        if (action.getComment() != null && !action.getComment().trim().isEmpty()) {
                            leaveRequest.setManagerComment(action.getComment());
                        } else {
                            leaveRequest.setManagerComment("Request " + finalStatus.toLowerCase());
                        }
                    });
        } else {
            // No explicit rejection stage found (might happen with cancellation)
            leaveRequest.setManagerComment("Request " + finalStatus.toLowerCase() + " by system");
        }

        // 5. CRITICAL: Reverse the balance deduction
        try {
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    leaveRequest.getEmployee().getEmployeeId(),
                    leaveRequest.getLeaveType().getLeaveTypeId(),
                    leaveRequest.getDaysRequested(),
                    leaveRequest.getRequestDate().getYear()
            );
            log.info("Successfully reversed balance for {} LeaveRequest {}. Credited {} days back to {}.",
                    finalStatus, leaveRequest.getLeaveId(), leaveRequest.getDaysRequested(), 
                    leaveRequest.getEmployee().getEmployeeId());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to reverse balance for {} LeaveRequest {}. Manual intervention required!",
                    finalStatus, leaveRequest.getLeaveId(), e);
            // Still save the status update, but mark for manual review
            leaveRequest.setManagerComment(
                    (leaveRequest.getManagerComment() != null ? leaveRequest.getManagerComment() + ". " : "") +
                    "WARNING: Balance reversal failed - requires manual correction."
            );
        }

        // 6. Save updated LeaveRequest
        leaveRequestRepo.save(leaveRequest);

        log.info("Successfully processed {} LeaveRequest {}. Status updated, balance reversed.",
                finalStatus, leaveRequest.getLeaveId());

        // TODO: Send rejection notification email (deferred for now)
    }
}
