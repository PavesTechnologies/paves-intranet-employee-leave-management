package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.ApprovalActionDTO;
// Removed PendingApprovalTaskDTO import
import com.paves.employee_leave_management.dto.RejectionActionDTO;
import com.paves.employee_leave_management.entities.Employee; // Correct entity path
import com.paves.employee_leave_management.security.CurrentUser;
import com.paves.employee_leave_management.service.ApprovalQueryService;
import com.paves.employee_leave_management.service.ruleengine.WorkflowEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/approvals") // Base path for approval-related actions/queries
@RequiredArgsConstructor
@Slf4j
@CrossOrigin // Add if needed
public class ApprovalActionController {

    private final ApprovalQueryService approvalQueryService;
    private final WorkflowEngine workflowEngine;

    // --- Fetch Pending Tasks (Now returns FULL Details) ---

    @GetMapping("/my-pending/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Object>>> getMyPendingLeaveTasksWithDetails( // Updated return type
                                                                                        @CurrentUser Employee currentUser) {
        log.info("User {} fetching pending LEAVE task details.", currentUser.getEmployeeId());
        try {
            // Call the service method that returns full details
            List<Object> tasks = approvalQueryService.getPendingTaskDetailsForApproverByType( // Updated method call
                    currentUser.getEmployeeId(), "LEAVE");
            return ResponseEntity.ok(new ApiResponse<>(true, "Pending leave task details retrieved.", tasks));
        } catch (Exception e) {
            log.error("Error fetching pending leave task details for user {}: {}", currentUser.getEmployeeId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Error retrieving task details.", null));
        }
    }

    @GetMapping("/my-pending/hr-operations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Object>>> getMyPendingHrOperationTasksWithDetails( // Updated return type
                                                                                              @CurrentUser Employee currentUser) {
        log.info("User {} fetching pending HR_OPERATION task details.", currentUser.getEmployeeId());
        try {
            // Call the service method that returns full details
            List<Object> tasks = approvalQueryService.getPendingTaskDetailsForApproverByType( // Updated method call
                    currentUser.getEmployeeId(), "HR_OPERATION");
            return ResponseEntity.ok(new ApiResponse<>(true, "Pending HR operation task details retrieved.", tasks));
        } catch (Exception e) {
            log.error("Error fetching pending HR operation task details for user {}: {}", currentUser.getEmployeeId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Error retrieving task details.", null));
        }
    }

    // --- Individual Actions (Approve/Reject - No Changes Needed Here) ---

    @PostMapping("/stages/{stageId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> approveStage(
            @PathVariable UUID stageId,
            @RequestBody(required = false) ApprovalActionDTO actionDto,
            @CurrentUser Employee currentUser) {
        String approverId = currentUser.getEmployeeId();
        log.info("User {} attempting to APPROVE stage {}", approverId, stageId);
        try {
            workflowEngine.processAction(stageId, approverId, "APPROVE", null);
            return ResponseEntity.ok(new ApiResponse<>(true, "Stage approved successfully.", null));
        } catch (SecurityException | IllegalStateException e) {
            log.warn("Approval failed for stage {} by user {}: {}", stageId, approverId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (RuntimeException e) {
            log.error("Error approving stage {}: {}", stageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "An error occurred during approval.", null));
        }
    }

    @PostMapping("/stages/{stageId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> rejectStage(
            @PathVariable UUID stageId,
            @Valid @RequestBody RejectionActionDTO rejectionDto,
            @CurrentUser Employee currentUser) {
        String approverId = currentUser.getEmployeeId();
        log.info("User {} attempting to REJECT stage {}", approverId, stageId);
        try {
            workflowEngine.processAction(stageId, approverId, "REJECT", rejectionDto.getComment());
            return ResponseEntity.ok(new ApiResponse<>(true, "Stage rejected successfully.", null));
        } catch (SecurityException | IllegalStateException e) {
            log.warn("Rejection failed for stage {} by user {}: {}", stageId, approverId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (RuntimeException e) {
            log.error("Error rejecting stage {}: {}", stageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "An error occurred during rejection.", null));
        }
    }

    // --- Endpoint to fetch details for ONE specific stage ---
    @GetMapping("/stages/{stageId}/details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> getTaskDetails(
            @PathVariable UUID stageId,
            @CurrentUser Employee currentUser) {

        log.info("User {} fetching details for stage {}", currentUser.getEmployeeId(), stageId);
        try {
            // Call the service to get the combined details DTO
            Object taskDetails = approvalQueryService.getTaskDetails(stageId, currentUser.getEmployeeId());

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Task details retrieved successfully.",
                    taskDetails // Will be LeaveRequestDetailDTO or HrOperationRequestDetailDTO
            ));
        } catch (SecurityException e) {
            log.warn("Access denied for user {} on stage {}: {}", currentUser.getEmployeeId(), stageId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (RuntimeException e) { // Catch NotFound or other errors
            log.error("Error fetching details for stage {}: {}", stageId, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(false, e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "An error occurred while fetching task details.", null));
        }
    }
}