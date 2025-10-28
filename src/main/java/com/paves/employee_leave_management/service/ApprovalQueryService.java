package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.HrOperationRequestDetailDTO; // Import Detail DTO
import com.paves.employee_leave_management.dto.LeaveRequestDetailDTO; // Import Detail DTO
//import com.paves.employee_leave_management.dto.PendingApprovalTaskDTO;
//import com.paves.employee_leave_management.entity.approval.ApprovalAction; // Import Action entity
//import com.paves.employee_leave_management.entity.approval.ApprovalStage;
//import com.paves.employee_leave_management.entity.approval.Request;
//import com.paves.employee_leave_management.entity.hr.HrOperationRequest;
//import com.paves.employee_leave_management.entity.LeaveRequest;
//import com.paves.employee_leave_management.entity.user.Employee;
import com.paves.employee_leave_management.entities.*;
//import com.paves.employee_leave_management.repository.approval.ApprovalActionRepository; // Import Action repo
//import com.paves.employee_leave_management.repository.approval.ApprovalStageRepository;
//import com.paves.employee_leave_management.repository.hr.HrOperationRequestRepository;
//import com.paves.employee_leave_management.repo.LeaveRequestRepo;
//import com.paves.employee_leave_management.repository.user.EmployeeRepository;
import com.paves.employee_leave_management.repo.*;
import com.paves.employee_leave_management.service.ruleengine.DelegationService; // Import DelegationService
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime; // Import LocalDateTime
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApprovalQueryService {

    private final ApprovalStageRepository approvalStageRepository;
    private final EmployeeRepo employeeRepository;
    private final LeaveRequestRepo leaveRequestRepository;
    private final HrOperationRequestRepository hrOperationRequestRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final DelegationService delegationService;

    /**
     * Fetches pending ApprovalStages for an approver, filtered by request type,
     * returning the full details for each task.
     *
     * @param approverId  The Employee ID of the approver.
     * @param requestType The type of request ("LEAVE" or "HR_OPERATION").
     * @return A list of Objects, where each object is either LeaveRequestDetailDTO or HrOperationRequestDetailDTO.
     */
    public List<Object> getPendingTaskDetailsForApproverByType(String approverId, String requestType) {
        log.debug("Fetching PENDING task details for approver {} of type {}", approverId, requestType);

        // 1. Find PENDING stages for the approver
        List<ApprovalStage> pendingStages = approvalStageRepository.findByApproverIdAndStatus(approverId, "PENDING");

        // 2. Filter by request type
        List<ApprovalStage> filteredStages = pendingStages.stream()
                .filter(stage -> stage.getRequest() != null && requestType.equals(stage.getRequest().getRequestType()))
                .toList();

        // 3. Map each stage to its full details DTO
        List<Object> taskDetailsList = new ArrayList<>();
        for (ApprovalStage stage : filteredStages) {
            try {
                // Call the existing getTaskDetails method for each stage
                Object details = getTaskDetails(stage.getId(), approverId);
                taskDetailsList.add(details);
            } catch (Exception e) {
                // Log error for this specific stage but continue with others
                log.error("Error fetching details for stage {} while building pending list: {}", stage.getId(), e.getMessage());
                // Optionally add an error marker to the list or skip it
            }
        }
        return taskDetailsList;
    }

    /**
     * Fetches the complete details needed for an approver to review a specific task (stage).
     * (Code remains the same as previously provided)
     */
    @Transactional(readOnly = true)
    public Object getTaskDetails(UUID stageId, String currentUserId) {
        log.debug("Fetching details for stage ID: {}", stageId);

        // 1. Fetch the stage
        ApprovalStage stage = approvalStageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Approval Stage not found: " + stageId));

        // 2. Verify Access (Crucial Step)
        String designatedApproverId = stage.getApproverId();
        if (!designatedApproverId.equals(currentUserId)) {
            String activeDelegateId = delegationService.findActiveDelegate(designatedApproverId);
            if (!activeDelegateId.equals(currentUserId)) {
                log.warn("Access Denied: User {} attempted to access stage {} assigned to {} (Delegate: {}).",
                        currentUserId, stageId, designatedApproverId, activeDelegateId);
                throw new SecurityException("You are not the designated approver or active delegate for this stage.");
            }
            log.debug("User {} is accessing stage {} as a delegate for {}", currentUserId, stageId, designatedApproverId);
        }

        // 3. Fetch associated Request
        Request request = stage.getRequest();
        if (request == null) {
            log.error("Data Integrity Error: Associated Request not found for stage: {}", stageId);
            throw new RuntimeException("Associated Request data is missing for stage: " + stageId);
        }

        // 4. Fetch maker details
        Employee maker = employeeRepository.findById(request.getCreatedBy()).orElse(null);
        String makerName = (maker != null) ? maker.getFullName() : "Unknown [" + request.getCreatedBy() + "]";

        // 5. Fetch Last Action details for the entire Request
        ApprovalAction lastAction = approvalActionRepository
                .findTopByStage_RequestIdOrderByActionAtDesc(request.getId())
                .orElse(null);

        String lastActionById = null;
        String lastActionByName = null;
        LocalDateTime lastActionAt = null;
        String lastActionType = null;
        if (lastAction != null) {
            lastActionById = lastAction.getActionBy();
            lastActionAt = lastAction.getActionAt();
            lastActionType = lastAction.getActionType();
            Employee lastActor = employeeRepository.findById(lastActionById).orElse(null);
            lastActionByName = (lastActor != null) ? lastActor.getFullName() : "Unknown ["+lastActionById+"]";
        }

        // 6. Determine type and build the appropriate Detail DTO
        if ("LEAVE".equals(request.getRequestType())) {
            LeaveRequest leaveRequest = leaveRequestRepository.findById(request.getTargetEntityId())
                    .orElseThrow(() -> new RuntimeException("Original LeaveRequest " + request.getTargetEntityId() + " not found. Data may be inconsistent."));

            return LeaveRequestDetailDTO.builder()
                    .stageId(stage.getId())
                    .requestId(request.getId())
                    .leaveId(leaveRequest.getLeaveId())
                    .employeeId(leaveRequest.getEmployee().getEmployeeId())
                    .employeeName(leaveRequest.getEmployee().getFullName())
                    .leaveTypeName(leaveRequest.getLeaveType() != null ? leaveRequest.getLeaveType().getLeaveName() : "N/A")
                    .startDate(leaveRequest.getStartDate())
                    .endDate(leaveRequest.getEndDate())
                    .daysRequested(leaveRequest.getDaysRequested())
                    .reason(leaveRequest.getReason())
                    .driveLink(leaveRequest.getDriveLink())
                    .startSession(leaveRequest.getStartSession())
                    .endSession(leaveRequest.getEndSession())
                    .currentWorkflowStatus(request.getStatus())
                    .requestedAt(request.getCreatedAt())
                    .lastActionById(lastActionById)
                    .lastActionByName(lastActionByName)
                    .lastActionAt(lastActionAt)
                    .lastActionType(lastActionType)
                    .build();

        } else if ("HR_OPERATION".equals(request.getRequestType())) {
            HrOperationRequest hrOperationRequest = hrOperationRequestRepository.findById(UUID.fromString(request.getTargetEntityId()))
                    .orElseThrow(() -> new RuntimeException("Original HrOperationRequest " + request.getTargetEntityId() + " not found. Data may be inconsistent."));

            return HrOperationRequestDetailDTO.builder()
                    .stageId(stage.getId())
                    .requestId(request.getId())
                    .requestType(request.getRequestType())
                    .operationType(request.getOperationType())
                    .makerId(request.getCreatedBy())
                    .makerName(makerName)
                    .requestCreatedAt(request.getCreatedAt())
                    .currentWorkflowStatus(request.getStatus())
                    .hrOperationStatus(hrOperationRequest.getStatus())
                    .payload(hrOperationRequest.getPayload())
                    // Add Last Action Info if needed for HR Ops DTO
                    .build();
        } else {
            log.error("Unknown request type '{}' encountered for stage {}", request.getRequestType(), stageId);
            throw new RuntimeException("Unknown request type for stage: " + stageId);
        }
    }

    // --- Summary mapping methods are no longer called by the main pending list method ---
    // private PendingApprovalTaskDTO mapStageToTaskDTO(ApprovalStage stage) { ... }
    // private void fetchAndSetLeaveDetails(PendingApprovalTaskDTO.PendingApprovalTaskDTOBuilder builder, String leaveRequestId) { ... }
    // private void fetchAndSetHrOperationDetails(PendingApprovalTaskDTO.PendingApprovalTaskDTOBuilder builder, String hrOperationRequestId) { ... }

}