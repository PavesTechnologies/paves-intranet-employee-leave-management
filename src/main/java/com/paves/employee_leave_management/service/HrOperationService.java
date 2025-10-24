package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.HrOperationRequest; // Your new payload entity
import com.paves.employee_leave_management.entities.RuleSet;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.repo.RequestRepository;
import com.paves.employee_leave_management.repo.HrOperationRequestRepository;// Repository for the payload
import com.paves.employee_leave_management.service.ruleengine.RuleEvaluatorService;
import com.paves.employee_leave_management.service.ruleengine.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for initiating HR Operations workflows.
 * It prepares the data payload, creates the generic workflow Request,
 * and starts the approval process via the WorkflowEngine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HrOperationService {

    private final HrOperationRequestRepository hrOperationRequestRepository;
    private final RequestRepository requestRepository;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    // Inject EmployeeRepository or a similar service if needed to fetch full Employee details for makerAttributes

    /**
     * Submits a request to add a new Leave Type, triggering the approval workflow.
     *
     * @param maker         The Employee initiating the request.
     * @param leaveTypeData The data for the new LeaveType.
     * @return The created generic workflow Request object.
     */
    @Transactional
    public Request submitNewLeaveType(Employee maker, Object leaveTypeData) { // Use Object or specific DTO for data
        String operationType = "ADD_LEAVE_TYPE"; // Standardize operation types
        return submitHrOperation(maker, operationType, leaveTypeData);
    }

    /**
     * Submits a request to update an employee's leave balance, triggering the approval workflow.
     *
     * @param maker      The Employee initiating the request.
     * @param balanceUpdateData The data for the balance update (e.g., a DTO containing targetEmployeeId, leaveTypeId, adjustmentDays).
     * @return The created generic workflow Request object.
     */
    @Transactional
    public Request submitUpdateLeaveBalance(Employee maker, Object balanceUpdateData) {
        String operationType = "UPDATE_EMPLOYEE_BALANCE";
        return submitHrOperation(maker, operationType, balanceUpdateData);
    }

    /**
     * Submits a request to deactivate a Leave Type, triggering the approval workflow.
     *
     * @param maker      The Employee initiating the request.
     * @param deactivateData Data needed for deactivation (e.g., Map containing leaveTypeId).
     * @return The created generic workflow Request object.
     */
    @Transactional
    public Request submitDeactivateLeaveType(Employee maker, Object deactivateData) {
        String operationType = "DEACTIVATE_LEAVE_TYPE";
        return submitHrOperation(maker, operationType, deactivateData);
    }


    // Add similar submit methods for other HR operations...


    /**
     * Generic internal method to handle the submission of any HR operation.
     */
    private Request submitHrOperation(Employee maker, String operationType, Object dataPayload) {
        log.info("Submitting HR Operation '{}' initiated by {}", operationType, maker.getEmployeeId());

        // 1. Create and save the specific HR Operation Data Payload
        HrOperationRequest hrData = createHrOperationPayload(maker, operationType, dataPayload);

        // 2. Build the Maker Attributes JSON (snapshot for the rule engine)
        String makerAttributes = buildMakerAttributesJson(maker);

        // 3. Create the Generic Workflow Request (the "wrapper")
        Request workflowRequest = Request.builder()
                .createdBy(maker.getEmployeeId())
                .requestType("HR_OPERATION") // Distinguishes from "LEAVE" requests
                .operationType(operationType) // Specific action (e.g., ADD_LEAVE_TYPE)
                .status("PENDING")            // Initial status of the workflow
                .targetEntityId(hrData.getId().toString()) // LINK to the HrOperationRequest payload
                .makerAttributes(makerAttributes)      // Snapshot of the maker
                .build();
        requestRepository.save(workflowRequest);

        // 4. Link the payload back to the workflow Request (optional but good for tracking)
        hrData.setWorkflowRequestId(workflowRequest.getId());
        hrOperationRequestRepository.save(hrData);

        // 5. Evaluate rules to find the correct workflow definition (RuleSet)
        RuleSet matchedRule = ruleEvaluatorService.evaluate(workflowRequest)
                .orElseThrow(() -> {
                    log.error("No approval RuleSet found for HR Operation: {}", operationType);
                    // Rollback? You might want this method to throw a specific exception
                    // that the controller handles, potentially rolling back the transaction.
                    return new RuntimeException("Configuration Error: No approval rule found for operation: " + operationType);
                });

        // 6. Start the workflow engine using the matched RuleSet
        workflowEngine.startWorkflow(workflowRequest, matchedRule);

        log.info("Started workflow {} (RuleSet: {}) for HR Operation payload {}",
                workflowRequest.getId(), matchedRule.getName(), hrData.getId());

        return workflowRequest; // Return the created workflow Request
    }

    /**
     * Creates and persists the HrOperationRequest entity containing the data payload.
     */
    private HrOperationRequest createHrOperationPayload(Employee maker, String operationType, Object data) {
        try {
            String payloadJson = objectMapper.writeValueAsString(data); // Serialize the data DTO/Map to JSON

            HrOperationRequest hrData = HrOperationRequest.builder()
                    .operationType(operationType)
                    .createdByEmployeeId(maker.getEmployeeId())
                    .payload(payloadJson)
                    .status("PENDING_APPROVAL") // Initial status of the data payload itself
                    .build();
            return hrOperationRequestRepository.save(hrData);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize HR operation payload for type {}", operationType, e);
            // This is a critical error, should likely stop the process
            throw new RuntimeException("Error serializing operation payload", e);
        }
    }

    /**
     * Builds a JSON string representing the maker's attributes relevant for rule evaluation.
     */
    private String buildMakerAttributesJson(Employee maker) {
        // Fetch necessary details if not already loaded (consider eager/lazy loading on Employee)
        try {
            // Include attributes that your RuleConditions might check
            Map<String, String> attributes = Map.of(
                    "role", maker.getRole() != null ? maker.getRole() : "",
                    "departmentId", maker.getDepartment() != null ? maker.getDepartment().getId().toString() : "",
                    "groupId", maker.getGroup() != null ? maker.getGroup().getId().toString() : ""
                    // Add grade, location, jobTitle, etc. if needed by rules
            );
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize maker attributes for employee {}", maker.getEmployeeId(), e);
            return "{}"; // Return empty JSON on error
        } catch (NullPointerException npe) {
            log.error("Null pointer exception while accessing maker attributes for employee {}. Check lazy loading.", maker.getEmployeeId(), npe);
            // Handle cases where Department or Group might be null or lazily loaded
            // Consider fetching the Employee entity with necessary joins beforehand if this happens often.
            return "{}";
        }
    }
}