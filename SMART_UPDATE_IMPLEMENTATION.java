// ==================== SMART UPDATE WITH LEVEL PRESERVATION ====================
// Replace the existing updateRequestByEmployee method in LeaveRequestService with this implementation

/**
 * Smart update for employee-initiated leave request changes.
 * Assesses change impact and preserves approval progress when possible.
 */
@Override
@Transactional
public ValidationResultDTO updateRequestByEmployee(LeaveRequest originalLeaveRequest, LeaveRequestValidationDTO updatedDetailsDto) {

    log.info("Attempting SMART UPDATE for Leave Request {} by employee {}",
            originalLeaveRequest.getLeaveId(), originalLeaveRequest.getEmployee().getEmployeeId());

    // --- 1. Assess Change Impact ---
    LeaveChangeDetails changeDetails = assessChangeImpact(originalLeaveRequest, updatedDetailsDto);
    
    if (changeDetails.getChanges().isEmpty()) {
        log.info("No changes detected for Leave Request {}", originalLeaveRequest.getLeaveId());
        ValidationResultDTO result = ValidationResultDTO.builder()
                .valid(true)
                .leaveId(originalLeaveRequest.getLeaveId())
                .build();
        result.addMessage("No changes detected. Leave request remains unchanged.");
        return result;
    }

    // --- 2. Find the associated workflow Request ---
    Request workflowRequest = requestRepository.findByTargetEntityId(originalLeaveRequest.getLeaveId())
            .orElse(null);

    // --- 3. Check Workflow Status ---
    if (workflowRequest == null) {
        log.error("Cannot update Leave Request {}: Corresponding workflow Request not found.", originalLeaveRequest.getLeaveId());
        if (originalLeaveRequest.getStatus() != LeaveStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot update: Workflow not found and Leave Request is not in a pending state.");
        }
        log.warn("Workflow Request not found for Leave Request {}. Proceeding with basic update.", originalLeaveRequest.getLeaveId());
    } else if (!"PENDING".equals(workflowRequest.getStatus())) {
        log.warn("Cannot update Leave Request {}: Workflow {} is already finalized with status {}.",
                originalLeaveRequest.getLeaveId(), workflowRequest.getId(), workflowRequest.getStatus());
        throw new IllegalStateException("Cannot update a leave request once the approval process is complete or rejected.");
    }

    // --- 4. Route to appropriate update strategy based on impact ---
    ValidationResultDTO result;
    switch (changeDetails.getImpact()) {
        case MAJOR:
            log.info("MAJOR changes detected for Leave Request {}. Resetting workflow.", originalLeaveRequest.getLeaveId());
            result = handleMajorUpdate(originalLeaveRequest, updatedDetailsDto, workflowRequest, changeDetails);
            break;
            
        case MINOR:
            log.info("MINOR changes detected for Leave Request {}. Preserving workflow progress.", originalLeaveRequest.getLeaveId());
            result = handleMinorUpdate(originalLeaveRequest, updatedDetailsDto, workflowRequest, changeDetails);
            break;
            
        case TRIVIAL:
            log.info("TRIVIAL changes detected for Leave Request {}. Updating without workflow impact.", originalLeaveRequest.getLeaveId());
            result = handleTrivialUpdate(originalLeaveRequest, updatedDetailsDto, changeDetails);
            break;
            
        default:
            throw new IllegalStateException("Unknown change impact: " + changeDetails.getImpact());
    }
    
    return result;
}

/**
 * Handles MAJOR updates - requires complete workflow reset.
 * Cancels old workflow and starts new one.
 */
private ValidationResultDTO handleMajorUpdate(LeaveRequest originalLeaveRequest, 
                                              LeaveRequestValidationDTO updatedDetailsDto,
                                              Request workflowRequest,
                                              LeaveChangeDetails changeDetails) {
    
    log.info("Processing MAJOR update - Full workflow reset for Leave Request {}", originalLeaveRequest.getLeaveId());

    // --- 1. Reverse the Original Balance Deduction ---
    try {
        leaveBalanceService.updateLeaveBalanceAfterRejected(
                originalLeaveRequest.getEmployee().getEmployeeId(),
                originalLeaveRequest.getLeaveType().getLeaveTypeId(),
                originalLeaveRequest.getDaysRequested(),
                originalLeaveRequest.getRequestDate().getYear()
        );
    } catch (Exception e) {
        log.error("CRITICAL: Failed to reverse original balance deduction. Rolling back.", e);
        throw new RuntimeException("Failed to reverse original balance. Update aborted.", e);
    }

    // --- 2. Validate NEW Details ---
    updatedDetailsDto.setLeaveId(originalLeaveRequest.getLeaveId());
    ValidationResultDTO validationResult = validateLeaveRequest(updatedDetailsDto);
    if (!validationResult.isValid()) {
        throw new RuntimeException("Validation failed: " + String.join("; ", validationResult.getErrors()));
    }

    // --- 3. Cancel Old Workflow ---
    if (workflowRequest != null) {
        workflowRequest.setStatus("CANCELLED");
        requestRepository.save(workflowRequest);

        List<ApprovalStage> activeStages = approvalStageRepository.findByRequestIdAndStatusIn(
                workflowRequest.getId(), List.of("PENDING", "WAITING")
        );
        
        for (ApprovalStage stage : activeStages) {
            log.info("Cancelling stage for approver {} due to major update", stage.getApproverId());
            stage.setStatus("CANCELLED");
        }
        approvalStageRepository.saveAll(activeStages);
        eventPublisher.publishEvent(new WorkflowCompletionEvent(this, workflowRequest));
    }

    // --- 4. Update LeaveRequest Entity ---
    updateLeaveRequestFields(originalLeaveRequest, updatedDetailsDto);
    LeaveRequest updatedLeaveRequest = leaveRequestRepo.save(originalLeaveRequest);

    // --- 5. Deduct New Balance ---
    try {
        leaveBalanceService.updateLeaveBalanceAfterApproval(
                updatedLeaveRequest.getEmployee().getEmployeeId(),
                updatedLeaveRequest.getLeaveType().getLeaveTypeId(),
                updatedLeaveRequest.getDaysRequested(),
                updatedLeaveRequest.getRequestDate().getYear()
        );
    } catch (Exception e) {
        log.error("CRITICAL: Failed to deduct new balance. Rolling back.", e);
        throw new RuntimeException("Failed to deduct new balance. Update aborted.", e);
    }

    // --- 6. Start NEW Workflow ---
    String makerAttributes = buildMakerAttributesJson(updatedLeaveRequest.getEmployee());
    Request newWorkflowRequest = Request.builder()
            .createdBy(updatedLeaveRequest.getEmployee().getEmployeeId())
            .requestType("LEAVE")
            .operationType("APPLY")
            .status("PENDING")
            .targetEntityId(updatedLeaveRequest.getLeaveId())
            .leaveType(updatedLeaveRequest.getLeaveType().getLeaveTypeId())
            .totalDays((int) updatedLeaveRequest.getDaysRequested())
            .makerAttributes(makerAttributes)
            .build();
    Request savedNewWorkflowRequest = requestRepository.save(newWorkflowRequest);

    RuleSet matchedRule = ruleEvaluatorService.evaluate(savedNewWorkflowRequest)
            .orElseThrow(() -> new RuntimeException("No matching approval rule found."));
    workflowEngine.startWorkflow(savedNewWorkflowRequest, matchedRule);

    validationResult.addMessage("Leave request updated successfully. Workflow reset due to significant changes.");
    validationResult.addMessage("Changes: " + String.join(", ", changeDetails.getChanges()));
    validationResult.setLeaveId(updatedLeaveRequest.getLeaveId());
    return validationResult;
}

/**
 * Handles MINOR updates - preserves workflow progress, updates in place.
 */
private ValidationResultDTO handleMinorUpdate(LeaveRequest originalLeaveRequest,
                                              LeaveRequestValidationDTO updatedDetailsDto,
                                              Request workflowRequest,
                                              LeaveChangeDetails changeDetails) {
    
    log.info("Processing MINOR update - Preserving workflow for Leave Request {}", originalLeaveRequest.getLeaveId());

    // --- 1. Handle balance adjustment if duration changed ---
    if (changeDetails.isDurationChanged()) {
        double difference = updatedDetailsDto.getDaysRequested() - originalLeaveRequest.getDaysRequested();
        
        if (difference > 0) {
            leaveBalanceService.updateLeaveBalanceAfterApproval(
                    originalLeaveRequest.getEmployee().getEmployeeId(),
                    originalLeaveRequest.getLeaveType().getLeaveTypeId(),
                    difference,
                    originalLeaveRequest.getRequestDate().getYear()
            );
        } else {
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    originalLeaveRequest.getEmployee().getEmployeeId(),
                    originalLeaveRequest.getLeaveType().getLeaveTypeId(),
                    Math.abs(difference),
                    originalLeaveRequest.getRequestDate().getYear()
            );
        }
    }

    // --- 2. Validate updated details ---
    updatedDetailsDto.setLeaveId(originalLeaveRequest.getLeaveId());
    ValidationResultDTO validationResult = validateLeaveRequest(updatedDetailsDto);
    if (!validationResult.isValid()) {
        throw new RuntimeException("Validation failed: " + String.join("; ", validationResult.getErrors()));
    }

    // --- 3. Update LeaveRequest (preserve approval state) ---
    originalLeaveRequest.setStartDate(updatedDetailsDto.getStartDate());
    originalLeaveRequest.setEndDate(updatedDetailsDto.getEndDate());
    originalLeaveRequest.setDaysRequested(updatedDetailsDto.getDaysRequested());
    originalLeaveRequest.setReason(updatedDetailsDto.getReason());
    originalLeaveRequest.setDriveLink(updatedDetailsDto.getDriveLink());
    originalLeaveRequest.setStartSession(updatedDetailsDto.getStartSession());
    originalLeaveRequest.setEndSession(updatedDetailsDto.getEndSession());
    // DO NOT reset status, approvedBy - preserve approval progress
    
    LeaveRequest updatedLeaveRequest = leaveRequestRepo.save(originalLeaveRequest);

    // --- 4. Update workflow Request in place ---
    if (workflowRequest != null) {
        workflowRequest.setTotalDays((int) updatedDetailsDto.getDaysRequested());
        requestRepository.save(workflowRequest);
        
        // Notify pending approvers
        List<ApprovalStage> pendingStages = approvalStageRepository.findByRequestIdAndStatus(
                workflowRequest.getId(), "PENDING");
        
        for (ApprovalStage stage : pendingStages) {
            log.info("Notifying approver {} of minor changes", stage.getApproverId());
            // TODO: Send notification
        }
    }

    validationResult.addMessage("Leave request updated successfully. Approval progress preserved.");
    validationResult.addMessage("Changes: " + String.join(", ", changeDetails.getChanges()));
    validationResult.setLeaveId(updatedLeaveRequest.getLeaveId());
    return validationResult;
}

/**
 * Handles TRIVIAL updates - simple field updates without workflow impact.
 */
private ValidationResultDTO handleTrivialUpdate(LeaveRequest originalLeaveRequest,
                                                LeaveRequestValidationDTO updatedDetailsDto,
                                                LeaveChangeDetails changeDetails) {
    
    log.info("Processing TRIVIAL update for Leave Request {}", originalLeaveRequest.getLeaveId());

    if (changeDetails.isDocumentationChanged()) {
        originalLeaveRequest.setDriveLink(updatedDetailsDto.getDriveLink());
    }
    
    LeaveRequest updatedLeaveRequest = leaveRequestRepo.save(originalLeaveRequest);

    ValidationResultDTO validationResult = ValidationResultDTO.builder()
            .valid(true)
            .leaveId(updatedLeaveRequest.getLeaveId())
            .build();
    
    validationResult.addMessage("Leave request updated successfully. Minor documentation changes applied.");
    validationResult.addMessage("Changes: " + String.join(", ", changeDetails.getChanges()));
    return validationResult;
}

/**
 * Helper method to update all leave request fields.
 */
private void updateLeaveRequestFields(LeaveRequest leaveRequest, LeaveRequestValidationDTO dto) {
    LeaveType newLeaveType = leaveTypeRepo.findById(dto.getLeaveTypeId())
            .orElseThrow(() -> new RuntimeException("Leave type not found: " + dto.getLeaveTypeId()));

    leaveRequest.setLeaveType(newLeaveType);
    leaveRequest.setStartDate(dto.getStartDate());
    leaveRequest.setEndDate(dto.getEndDate());
    leaveRequest.setDaysRequested(dto.getDaysRequested());
    leaveRequest.setReason(dto.getReason());
    leaveRequest.setDriveLink(dto.getDriveLink());
    leaveRequest.setStartSession(dto.getStartSession());
    leaveRequest.setEndSession(dto.getEndSession());
    leaveRequest.setStatus(LeaveStatus.PENDING_APPROVAL);
    leaveRequest.setApprovedBy(null);
    leaveRequest.setResponseDate(null);
    leaveRequest.setManagerComment(null);
}

// ==================== APPROVER-INITIATED UPDATE ====================

/**
 * Allows an approver (manager/HR) to update a leave request during approval process.
 * Different from employee updates - approver can make corrections without resetting workflow.
 */
@Transactional
public ValidationResultDTO updateRequestByApprover(ApproverUpdateRequestDTO updateRequest) {
    
    log.info("Approver {} attempting to update Leave Request {}", 
        updateRequest.getApproverId(), updateRequest.getLeaveId());

    // --- 1. Find leave request ---
    LeaveRequest leaveRequest = leaveRequestRepo.findById(updateRequest.getLeaveId())
            .orElseThrow(() -> new RuntimeException("Leave request not found: " + updateRequest.getLeaveId()));

    // --- 2. Verify approver authorization ---
    Request workflowRequest = requestRepository.findByTargetEntityId(updateRequest.getLeaveId())
            .orElseThrow(() -> new RuntimeException("Workflow not found"));

    List<ApprovalStage> approverStages = approvalStageRepository.findByRequestIdAndApproverId(
            workflowRequest.getId(), updateRequest.getApproverId());
    
    if (approverStages.isEmpty()) {
        throw new SecurityException("Approver not authorized to update this request.");
    }

    // --- 3. Apply updates ---
    boolean hasChanges = false;
    List<String> changes = new ArrayList<>();

    if (updateRequest.getStartDate() != null && !updateRequest.getStartDate().equals(leaveRequest.getStartDate())) {
        leaveRequest.setStartDate(updateRequest.getStartDate());
        changes.add("Start date updated by approver");
        hasChanges = true;
    }

    if (updateRequest.getEndDate() != null && !updateRequest.getEndDate().equals(leaveRequest.getEndDate())) {
        leaveRequest.setEndDate(updateRequest.getEndDate());
        changes.add("End date updated by approver");
        hasChanges = true;
    }

    if (updateRequest.getDaysRequested() != null) {
        double difference = updateRequest.getDaysRequested() - leaveRequest.getDaysRequested();
        
        if (Math.abs(difference) > 0.01) {
            // Adjust balance
            if (difference > 0) {
                leaveBalanceService.updateLeaveBalanceAfterApproval(
                        leaveRequest.getEmployee().getEmployeeId(),
                        leaveRequest.getLeaveType().getLeaveTypeId(),
                        difference,
                        leaveRequest.getRequestDate().getYear()
                );
            } else {
                leaveBalanceService.updateLeaveBalanceAfterRejected(
                        leaveRequest.getEmployee().getEmployeeId(),
                        leaveRequest.getLeaveType().getLeaveTypeId(),
                        Math.abs(difference),
                        leaveRequest.getRequestDate().getYear()
                );
            }
            
            leaveRequest.setDaysRequested(updateRequest.getDaysRequested());
            changes.add("Duration updated by approver");
            hasChanges = true;
        }
    }

    if (updateRequest.getReason() != null && !updateRequest.getReason().equals(leaveRequest.getReason())) {
        leaveRequest.setReason(updateRequest.getReason());
        changes.add("Reason updated by approver");
        hasChanges = true;
    }

    if (updateRequest.getDriveLink() != null) {
        leaveRequest.setDriveLink(updateRequest.getDriveLink());
        changes.add("Documentation updated by approver");
        hasChanges = true;
    }

    if (!hasChanges) {
        ValidationResultDTO result = ValidationResultDTO.builder()
                .valid(true)
                .leaveId(leaveRequest.getLeaveId())
                .build();
        result.addMessage("No changes detected.");
        return result;
    }

    // --- 4. Save ---
    LeaveRequest updatedRequest = leaveRequestRepo.save(leaveRequest);

    // --- 5. Notify employee ---
    if (Boolean.TRUE.equals(updateRequest.getNotifyEmployee())) {
        log.info("Notifying employee of approver-initiated changes");
        // TODO: Send notification
    }

    log.info("Approver {} updated Leave Request {}. Changes: {}",
        updateRequest.getApproverId(), updateRequest.getLeaveId(), String.join(", ", changes));

    ValidationResultDTO result = ValidationResultDTO.builder()
            .valid(true)
            .leaveId(updatedRequest.getLeaveId())
            .build();
    
    result.addMessage("Leave request updated successfully by approver.");
    result.addMessage("Changes: " + String.join(", ", changes));
    result.addMessage("Update reason: " + updateRequest.getUpdateReason());

    return result;
}
