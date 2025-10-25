package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.LeaveBalanceUpdateHandleDTO; // Example DTO
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.HrOperationRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.HrOperationRequestRepository;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface; // Use interfaces
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;   // Use interfaces
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for executing the actual business logic for HR Operations
 * *after* the corresponding workflow Request has been approved.
 * Triggered by the WorkflowCompletionListener.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HrOperationProcessorService {

    private final HrOperationRequestRepository hrOperationRequestRepository;
    private final LeaveTypeServiceInterface leaveTypeService;         // Inject your existing service interface
    private final LeaveBalanceServiceInterface leaveBalanceService; // Inject your existing service interface
    private final ObjectMapper objectMapper;

    /**
     * Processes an approved HR Operation workflow Request.
     * Fetches the data payload and executes the corresponding business logic.
     *
     * @param approvedRequest The generic Request object marked as APPROVED.
     */
    @Transactional
    public void process(Request approvedRequest) {
        // Ensure this processor only handles APPROVED HR operations
        if (!"APPROVED".equals(approvedRequest.getStatus()) || !"HR_OPERATION".equals(approvedRequest.getRequestType())) {
            log.warn("HrOperationProcessor received a request that is not an approved HR Operation (ID: {}, Status: {}, Type: {}). Skipping.",
                    approvedRequest.getId(), approvedRequest.getStatus(), approvedRequest.getRequestType());
            return;
        }

        // 1. Fetch the specific HR Operation data payload using the link
        HrOperationRequest hrData = hrOperationRequestRepository
                .findById(UUID.fromString(approvedRequest.getTargetEntityId()))
                .orElseThrow(() -> {
                    log.error("CRITICAL: HR Operation data payload not found for approved workflow Request: {}", approvedRequest.getId());
                    // This indicates a potential data integrity issue. Needs alerting/monitoring.
                    return new RuntimeException("HR Operation data payload not found for workflow: " + approvedRequest.getId());
                });

        // 2. Defensive Check: Ensure the payload hasn't already been processed
        if (!"PENDING_APPROVAL".equals(hrData.getStatus())) {
            log.warn("HR Operation data payload {} already processed or in unexpected state {}. Skipping execution for workflow {}.",
                    hrData.getId(), hrData.getStatus(), approvedRequest.getId());
            return;
        }

        log.info("Executing business logic for approved HR Operation: {} (Workflow ID: {})",
                hrData.getId(), approvedRequest.getId());

        try {
            // 3. Execute the specific business logic based on the operation type
            switch (approvedRequest.getOperationType()) {

                case "ADD_LEAVE_TYPE":
                    // Deserialize the payload JSON into the LeaveType object
                    LeaveType newLeaveType = objectMapper.readValue(hrData.getPayload(), LeaveType.class);
                    // --- CALL THE ACTUAL BUSINESS LOGIC ---
                    leaveTypeService.addLeaveType(newLeaveType);
                    log.info("Successfully added Leave Type {} via approved workflow.", newLeaveType.getLeaveTypeId());
                    break;

                case "UPDATE_EMPLOYEE_BALANCE":
                    // Deserialize payload into appropriate DTO/Map
                    // Example using a specific DTO (adjust if you use Map)

                    LeaveBalanceUpdateRequest balanceUpdate = objectMapper.readValue(hrData.getPayload(), LeaveBalanceUpdateRequest.class);
                    // --- CALL THE ACTUAL BUSINESS LOGIC ---
                    // Assuming updateLeaveBalancesFromHr takes the DTO directly
                    leaveBalanceService.updateLeaveBalancesFromHr(balanceUpdate);
                    log.info("Successfully updated leave balance via approved workflow for payload {}", hrData.getId());
                    break;

                case "DEACTIVATE_LEAVE_TYPE":
                    // Deserialize payload (might just be a Map with leaveTypeId)
                    Map<String, String> deactivatePayload = objectMapper.readValue(hrData.getPayload(), new TypeReference<Map<String, String>>() {});
                    String leaveTypeIdToDeactivate = deactivatePayload.get("leaveTypeId");
                    // --- CALL THE ACTUAL BUSINESS LOGIC ---
                    leaveTypeService.deActiveLeaveType(leaveTypeIdToDeactivate); // Assuming method name
                    log.info("Successfully deactivated Leave Type {} via approved workflow.", leaveTypeIdToDeactivate);
                    break;

                // Add cases for UPDATE_LEAVE_TYPE, ADD_HOLIDAY, etc.

                default:
                    log.warn("Unsupported HR operation type received by processor: {}", approvedRequest.getOperationType());
                    // Mark as failed? Or just log? Depends on requirements.
                    throw new UnsupportedOperationException("Unsupported HR operation type: " + approvedRequest.getOperationType());
            }

            // 4. Mark the HR Operation payload as COMPLETED
            hrData.setStatus("COMPLETED");

        } catch (Exception e) {
            // 5. Handle any exception during business logic execution
            hrData.setStatus("FAILED_PROCESSING");
            log.error("Failed to execute business logic for approved HR operation {} (Workflow: {}): {}",
                    hrData.getId(), approvedRequest.getId(), e.getMessage(), e);
            // Re-throw the exception so the transaction rolls back unless specific retry logic is needed.
            // Consider specific exceptions for business validation errors vs. technical errors.
            throw new RuntimeException("Processing failed for HR Operation " + hrData.getId(), e);
        } finally {
            // 6. Always save the final status of the HR Operation payload
            hrOperationRequestRepository.save(hrData);
        }
    }
}