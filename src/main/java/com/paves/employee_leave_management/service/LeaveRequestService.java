package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.ChangeImpact;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.event.WorkflowCompletionEvent;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.repo.RequestRepository; // New import
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.paves.employee_leave_management.service.ruleengine.RuleEvaluatorService; // New import
import com.paves.employee_leave_management.service.ruleengine.WorkflowEngine;


import java.util.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

import com.paves.employee_leave_management.repo.ApprovalStageRepository; // Add repository for stages
import com.paves.employee_leave_management.entities.ApprovalStage; // Add stage entity
import org.springframework.context.ApplicationEventPublisher;

@Service
@Transactional
@RequiredArgsConstructor // Use Lombok for constructor injection
@Slf4j
public class LeaveRequestService implements LeaveRequestServiceInterface {

    @Autowired
    private LeaveRequestRepo leaveRequestRepo;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private LeaveTypeRepo leaveTypeRepo;

    @Autowired
    private EmployeeServiceInterface employeeService;

    @Autowired
    private LeaveTypeServiceInterface leaveTypeService;

    @Autowired
    private LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    private EmailServiceInterface emailService;

    @Autowired
    private ApprovalStageRepository approvalStageRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final RequestRepository requestRepository;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private static final Pattern GOOGLE_DRIVE_URL_PATTERN = Pattern.compile(
            "^https?://(drive|docs)\\.google\\.com/(file/d/|folders/|spreadsheets/d/|document/d/|open\\?id=)([a-zA-Z0-9_-]+)(/.*)?$"
    );

    // ==================== VALIDATION METHODS ====================

    /**
     * Main validation method that orchestrates all validation checks
     */
    @Override
    public ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO result = ValidationResultDTO.builder()
                .isValid(true)
                .employeeId(request.getEmployeeId())
                .requestedDays((float) request.getDaysRequested())
                .build();

        // Get employee and leave type info
        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();
        LeaveType leaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();

        // Validate basic requirements
        if (!validateBasicRequirements(request, result, employee, leaveType)) {
            return result;
        }

        if (employee != null) {
            result.setEmployeeName(employee.getFullName());
        }

        // Perform all validation checks
        validateBasicDateConstraints(request, result);
        validateLeaveBalance(request, result, employee, leaveType);
        validateLeaveConflicts(request, result);
        validateLeaveTypeSpecificRules(request, result, employee, leaveType);

        return result;
    }

    /**
     * Validation method for LeaveRequest entity
     */
    @Override
    public ValidationResultDTO validateLeaveRequestEntity(LeaveRequest request) {
        LeaveRequestValidationDTO dto = LeaveRequestValidationDTO.builder()
                .employeeId(request.getEmployee().getEmployeeId())
                .leaveTypeId(request.getLeaveType().getLeaveTypeId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .daysRequested(request.getDaysRequested())
                .reason(request.getReason())
                .driveLink(request.getDriveLink())
                .build();

        return validateLeaveRequest(dto);
    }

    /**
     * Validates basic requirements: employee exists, leave type exists, reason provided
     */
    private boolean validateBasicRequirements(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                              Employee employee, LeaveType leaveType) {
        if (employee == null) {
            result.addError("Employee not found");
            return false;
        }

        if (leaveType == null) {
            result.addError("Leave type not found");
            return false;
        }

        // Validate reason is provided (all leave types require comments as per business rules)
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            result.addError("Leave reason/comments are mandatory");
        }

        // Validate drive link for documentation requirements
        validateDriveLinkRequirements(request, result, leaveType);

        return true;
    }

    /**
     * Validates drive link requirements based on leave type documentation needs
     */
    private void validateDriveLinkRequirements(LeaveRequestValidationDTO request, ValidationResultDTO result, LeaveType leaveType) {
        // Check if leave type requires documentation
        System.out.println("From ValidateDriveLinkRequirements");
        if (leaveType.getRequiresDocumentation() && !leaveType.getLeaveTypeId().equals("L-SL")) {
            // For leave types that require documentation, drive link should be provided
            System.out.println("From ValidateDriveLinkRequirements inside");
            if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                result.addError("Drive link with supporting documents is required for " + leaveType.getLeaveName());
            } else {
                // Validate drive link format (basic URL validation)
                validateDriveLinkFormat(request.getDriveLink(), result);
            }
        }

        // For sick leave specifically, check if drive link is required for longer durations
        if (leaveType.getLeaveTypeId().equals("L-SL") && request.getDaysRequested() > 3) {
            System.out.println("sgfhd;j");
            if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                result.addError("Drive link with medical certificate is mandatory for sick leave exceeding 3 days");
            }
            else{
                System.out.println("From ValidateDriveLinkRequirements -SL");
                validateDriveLinkFormat(request.getDriveLink(), result);
            }
        }
    }

    /**
     * Validates the format of the drive link URL
     */


    /**
     * Validates that a link is a correctly formatted Google Drive URL.
     * A null or empty link will now be considered an error.
     * @param driveLink The string URL to validate.
     * @param result The DTO to add errors to.
     */
    private void validateDriveLinkFormat(String driveLink, ValidationResultDTO result) {
        // Step 1: Check if the link is null or empty. If so, add an error and stop.
        System.out.println("From ValidateDriveLinkFormat");
        if (driveLink == null || driveLink.trim().isEmpty()) {
            result.addError("Drive link is required and cannot be empty.");
            return; // Stop further validation.
        }

        String trimmedLink = driveLink.trim();

        // Step 2: Match the link against the strict Google Drive pattern.
        if (!GOOGLE_DRIVE_URL_PATTERN.matcher(trimmedLink).matches()) {
            result.addError("Link must be a valid Google Drive URL format (e.g., drive.google.com/file/d/...).");
        }
    }
//    private void validateDriveLinkFormat(String driveLink, ValidationResultDTO result) {
//        if (driveLink != null && !driveLink.trim().isEmpty()) {
//            String trimmedLink = driveLink.trim();
//
//            // Basic URL format validation
//            if (!trimmedLink.startsWith("http://") && !trimmedLink.startsWith("https://")) {
//                result.addError("Drive link must be a valid URL starting with http:// or https://");
//                return;
//            }
//
//            // Check if it's a Google Drive link (optional - can be any cloud storage)
//            if (trimmedLink.contains("drive.google.com") || trimmedLink.contains("docs.google.com")) {
//                // Additional validation for Google Drive links if needed
//                if (!trimmedLink.contains("/") || trimmedLink.length() < 20) {
//                    result.addError("Invalid Google Drive link format");
//                }
//            }
//        }
//    }

    /**
     * Validates basic date constraints that apply to ALL leave types
     */
    private void    validateBasicDateConstraints(LeaveRequestValidationDTO request, ValidationResultDTO result) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        // End date must be after or equal to start date
        if (endDate.isBefore(startDate)) {
            result.addError("End date must be after or equal to start date");
        }
    }

    /**
     * Validates leave balance and employee eligibility
     */
    private void validateLeaveBalance(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                      Employee employee, LeaveType leaveType) {
        Integer currentYear = LocalDate.now().getYear();
        LeaveBalanceDTO balance = leaveBalanceService.getLeaveBalance(
                request.getEmployeeId(), request.getLeaveTypeId(), currentYear);

        if (balance == null) {
            result.addError("Leave balance not found for the current year");
            return;
        }

        // Check if employee has sufficient leave balance
        if (!leaveType.getLeaveTypeId().equals("L-UP") && !leaveType.getAllowNegativeBalance() &&
                balance.getRemainingLeaves() < request.getDaysRequested()) {
            result.addError(String.format(
                    "Insufficient %s balance. Available: %.2f days, Requested: %.2f days",
                    leaveType.getLeaveName(), balance.getRemainingLeaves(), request.getDaysRequested()));
        }

        // Check waiting period for new employees (exclude Unpaid Leave)
        if (!"L-UP".equalsIgnoreCase(leaveType.getLeaveTypeId()) &&
                leaveType.getWaitingPeriodDays() != null && leaveType.getWaitingPeriodDays() > 0) {
            LocalDate eligibleDate = employee.getHireDate().plusDays(leaveType.getWaitingPeriodDays());
            if (LocalDate.now().isBefore(eligibleDate)) {
                result.addError(String.format(
                        "Employee not eligible for %s. Waiting period: %d days from hire date",
                        leaveType.getLeaveName(), leaveType.getWaitingPeriodDays()));
            }
        }
    }

    /**
     * Validates overlapping leave requests
     */
    private void validateLeaveConflicts(LeaveRequestValidationDTO request, ValidationResultDTO result) {
        List<LeaveRequest> overlappingRequests = getOverlappingRequests(
                request.getEmployeeId(), request.getStartDate(), request.getEndDate());

        for (LeaveRequest existing : overlappingRequests) {
            if (existing.getLeaveId().equals(request.getLeaveId())) {
                continue;
            }
            result.addError(String.format(
                    "Leave request overlaps with existing %s leave from %s to %s",
                    existing.getLeaveType().getLeaveName(),
                    existing.getStartDate(),
                    existing.getEndDate()));
        }
    }

    /**
     * Validates leave type specific rules and constraints
     */
    private void validateLeaveTypeSpecificRules(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                                Employee employee, LeaveType leaveType) {
        String leaveTypeId = request.getLeaveTypeId();

        // Route to specific leave type validation methods
        switch (leaveTypeId.toUpperCase()) {
            case "L-ML":
                validateMaternityLeaveRules(request, result, employee, leaveType);
                break;
            case "L-PL":
                validatePaternityLeaveRules(request, result, employee, leaveType);
                break;
            case "L-COMPOFF":
                validateCompensatoryLeaveRules(request, result, employee, leaveType);
                break;
            case "L-SL":
                validateSickLeaveRules(request, result, employee, leaveType);
                break;
            case "L-EL":
                validateEarnedLeaveRules(request, result, employee, leaveType);
                break;
            case "L-UP":
                validateUnpaidLeaveRules(request, result, employee, leaveType);
                break;
            default:
                // For any other leave types, apply default date and notice validations
                validateDefaultLeaveRules(request, result, employee, leaveType);
                break;
        }

        // Validate half-day restrictions (applies to all leave types)
        if (!leaveType.getAllowHalfDay() && request.getDaysRequested() < 1) {
            result.addError(String.format("%s does not allow half-day leave", leaveType.getLeaveName()));
        }
    }

    /**
     * Validates maternity leave specific rules
     */
    private void validateMaternityLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Check for pending maternity leave requests
        int pendingCount = leaveRequestRepo.countPendingLeavesByType(employee.getEmployeeId(), "L-ML");
        if (pendingCount > 0) {
            result.addError("You already have a pending maternity leave request.");
            return;
        }

        // Fetch all approved maternity leaves
        List<LeaveRequest> approvedML = leaveRequestRepo.findApprovedLeavesByType(employee.getEmployeeId(), "L-ML");

        // Count how many approved maternity leaves were >= 48 days
        long longLeaves = approvedML.stream()
                .filter(lr -> lr.getDaysRequested() >= 48)
                .count();

        // If already taken 2 such long maternity leaves, block the new one
        if (request.getDaysRequested() >= 48 && longLeaves >= 2) {
            result.addError("Maternity leave for 6 months (48+ days) can only be availed twice.");
            return;
        }

        // If current request is for a long maternity leave but not exactly 180, warn
        if (request.getDaysRequested() >= 48 && request.getDaysRequested() != 180) {
            result.addError("Standard maternity leave should be exactly 180 days.");
        }

        // Validate past date restrictions
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);

        // Validate advance notice requirement
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    /**
     * Validates paternity leave specific rules
     */
    private void validatePaternityLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Check for pending paternity leave requests
        int pendingCount = leaveRequestRepo.countPendingLeavesByType(employee.getEmployeeId(), "L-PL");
        if (pendingCount > 0 && request.getLeaveId() == null) {
            result.addError("You already have a pending paternity leave request. Please wait for it to be approved or rejected.");
            return;
        }

        List<LeaveRequest> approvedPL = leaveRequestRepo.findApprovedLeavesByType(employee.getEmployeeId(), "L-PL");

        if (approvedPL.size() >= 2) {
            result.addError("Paternity leave can only be availed twice. You have already used the maximum limit.");
            return;
        }

        if (request.getDaysRequested() != 5) {
            result.addError("Paternity leave must be exactly 5 continuous days.");
        }

        // Check for 1-year gap if there's a prior leave
        if (approvedPL.size() == 1) {
            LeaveRequest previousLeave = approvedPL.get(0);
            long gap = ChronoUnit.DAYS.between(previousLeave.getStartDate(), request.getStartDate());
            if (gap < 365) {
                result.addError("There must be a minimum 1-year gap between two paternity leaves.");
            }
        }

        // Validate past date restrictions
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);

        // Validate advance notice requirement
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateCompensatoryLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Check if documentation is required
        if (leaveType.getRequiresDocumentation() && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            result.addError("Compensatory leave requires documentation/proof of overtime work");
        }

        // Apply standard date and notice validations
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateSickLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Check if documentation is required for sick leave
        if (leaveType.getRequiresDocumentation() && request.getDaysRequested() > 3 && request.getDriveLink() == null) {
            result.addError("Sick leave for more than 3 days requires medical certificate");
        }

        // Sick leave may have different date restrictions (allow past dates for emergencies)
        // For now, apply standard validations - can be customized later
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateEarnedLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Check waiting period for earned leave eligibility
        if (leaveType.getWaitingPeriodDays() > 0) {
            LocalDate eligibilityDate = employee.getHireDate().plusDays(leaveType.getWaitingPeriodDays());
            if (LocalDate.now().isBefore(eligibilityDate)) {
                result.addError(String.format("Earned leave requires %d days of service before eligibility",
                        leaveType.getWaitingPeriodDays()));
            }
        }

        // Apply standard date and notice validations
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateUnpaidLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Unpaid leave typically requires documentation
        if (leaveType.getRequiresDocumentation() && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            result.addError("Unpaid leave requires detailed justification");
        }

        // Apply standard date and notice validations
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateDefaultLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        // Apply standard date and notice validations for any leave type not explicitly handled
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    /**
     * Validates past date restrictions based on leave type policy
     */
    private void validatePastDateRestrictions(LocalDate startDate, LocalDate today, LeaveType leaveType, ValidationResultDTO result) {
        if (leaveType.getPastDateLimitDays() != null && leaveType.getPastDateLimitDays() > 0) {
            LocalDate pastLimit = today.minusDays(leaveType.getPastDateLimitDays());
            if (startDate.isBefore(pastLimit)) {
                result.addError(String.format("Cannot request leave more than %d days in the past",
                        leaveType.getPastDateLimitDays()));
            }
        } else if (startDate.isBefore(today.minusDays(1))) {
            // Default: no past dates allowed except today
            result.addError("Cannot request leave for past dates");
        }
    }

    /**
     * Validates advance notice requirement
     */
    private void validateAdvanceNoticeRequirement(LocalDate startDate, LocalDate today, LeaveType leaveType, ValidationResultDTO result) {
        if (leaveType.getAdvanceNoticeDays() != null && leaveType.getAdvanceNoticeDays() > 0) {
            long daysBetween = ChronoUnit.DAYS.between(today, startDate);
            if (daysBetween < leaveType.getAdvanceNoticeDays()) {
                result.addError(String.format("Leave request requires %d days advance notice",
                        leaveType.getAdvanceNoticeDays()));
            }
        }
    }

    // ==================== APPLICATION METHODS ====================

    /**
     * Save a new leave request after validation
     */
    @Override
    public LeaveRequest saveLeaveRequest(LeaveRequestValidationDTO request) {
        // Validate the request first
        ValidationResultDTO validationResult = validateLeaveRequest(request);

        if (!validationResult.isValid()) {
            throw new RuntimeException("Leave request validation failed: " +
                    String.join(", ", validationResult.getErrors()));
        }

        // Get employee and leave type
        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();
        LeaveType leaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();

        // Create and save the leave request
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .leaveType(leaveType)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .daysRequested(request.getDaysRequested())
                .reason(request.getReason())
                .driveLink(request.getDriveLink())
                .status(LeaveStatus.PENDING)
                .startSession(request.getStartSession())
                .endSession(request.getEndSession())
                .requestDate(LocalDate.now())
                .build();

        LeaveRequest savedRequest = leaveRequestRepo.save(leaveRequest);

        if (savedRequest != null) {
            // Update leave balance
            leaveBalanceService.updateLeaveBalanceAfterApproval(
                    savedRequest.getEmployee().getEmployeeId(),
                    savedRequest.getLeaveType().getLeaveTypeId(),
                    savedRequest.getDaysRequested(),
                    savedRequest.getRequestDate().getYear());

            // Send email notification to manager
            try {
                if (employee.getManager() != null && employee.getManager().getEmail() != null) {
                    emailService.sendLeaveApplicationNotification(
                            employee.getManager().getEmail(),
                            employee.getFullName(),
                            leaveType.getLeaveName(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString(),
                            request.getReason()
                    );
                }
            } catch (Exception e) {
                // Log the error but don't fail the request
                System.err.println("Failed to send email notification: " + e.getMessage());
            }

            // 4. Build Maker Attributes JSON
            String makerAttributes = buildMakerAttributesJson(employee);
            // 5. Create the Generic Workflow Request
            Request workflowRequest = Request.builder()
                    .createdBy(employee.getEmployeeId())
                    .requestType("LEAVE") // Hardcoded for leave requests
                    .operationType("APPLY") // Standard operation type for applying
                    .status("PENDING") // Initial status of the workflow itself
                    .targetEntityId(savedRequest.getLeaveId()) // LINK to the LeaveRequest!
                    .leaveType(leaveType.getLeaveTypeId()) // Copy for quick rule evaluation
                    .totalDays((int) savedRequest.getDaysRequested()) // Copy for quick rule evaluation
                    .makerAttributes(makerAttributes) // Pass maker context to rules
                    .build();
            Request savedWorkflowRequest = requestRepository.save(workflowRequest);
            // 6. Evaluate Rules and Start Workflow
            RuleSet matchedRule = ruleEvaluatorService.evaluate(savedWorkflowRequest)
                    .orElseThrow(() -> {
                        log.error("No matching approval RuleSet found for Leave Request {}. Rolling back.", savedRequest.getLeaveId());
                        // This MUST also trigger a rollback
                        return new RuntimeException("Configuration Error: No matching approval rule found for this leave request.");
                    });
            workflowEngine.startWorkflow(savedWorkflowRequest, matchedRule);

            // --- REMOVED LOGIC ---
            // NO email notification here (handled by engine/listener eventually)

            log.info("Leave request {} submitted and workflow {} started using RuleSet '{}'.",
                    savedRequest.getLeaveId(), savedWorkflowRequest.getId(), matchedRule.getName());
            return savedRequest;
        } else {
            return null;
        }
    }

    private String buildMakerAttributesJson(Employee employee) {
        // Ensure related entities are loaded if needed, or handle potential NullPointerExceptions
        String departmentId = (employee.getDepartment() != null && employee.getDepartment().getId() != null)
                ? employee.getDepartment().getId().toString() : "";
        String groupId = (employee.getGroup() != null && employee.getGroup().getId() != null)
                ? employee.getGroup().getId().toString() : "";

        try {
            Map<String, String> attributes = Map.of(
                    "role", employee.getRole() != null ? employee.getRole() : "",
                    "departmentId", departmentId,
                    "groupId", groupId,
                    "jobTitle", employee.getJobTitle() != null ? employee.getJobTitle() : ""
                    // Add grade, location etc. if needed by your rules
            );
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize maker attributes for employee {}", employee.getEmployeeId(), e);
            return "{}"; // Return empty JSON on error
        }
    }

// ...
    /**
     * Calculate working days between two dates (excluding weekends and holidays)
     */
    /**
     * Get all leave requests for an employee
     */
    @Override
    public List<LeaveRequest> getLeaveRequestsByEmployee(String employeeId) {
        return leaveRequestRepo.findByEmployee_EmployeeId(employeeId);
    }

    /**
     * Get a specific leave request by ID
     */
    @Override
    public LeaveRequest getLeaveRequestById(String leaveId) {
        return leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId));
    }

    /**
     * Cancel a leave request by employee (only if pending)
     */
    @Override
    public LeaveRequest cancelLeaveRequest(String leaveId, String employeeId) {
        LeaveRequest request = leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!request.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Unauthorized: You can only cancel your own leave requests");
        }

//        if (request.getStatus() != LeaveStatus.PENDING) {
//            throw new RuntimeException("Cannot cancel a leave request that is not pending");
//        }
        Request workflowRequest = requestRepository.findByTargetEntityId(leaveId) // Assumes you add this method
                .orElse(null);

        if (workflowRequest == null || !"PENDING".equals(workflowRequest.getStatus())) {
            // If workflow not found or already completed/rejected, use the LeaveRequest status
            if (request.getStatus() != LeaveStatus.PENDING_APPROVAL) {
                throw new RuntimeException("Cannot cancel a leave request that is no longer pending approval.");
            }
            // Edge case: Workflow finished but processor hasn't run? Handle carefully.
            log.warn("Workflow Request for Leave Request {} not found or not PENDING, but LeaveRequest status is {}. Proceeding with cancellation.", leaveId, request.getStatus());
        }


        log.info("Employee {} cancelling Leave Request {}", employeeId, leaveId);
        request.setStatus(LeaveStatus.CANCELLED);
        request.setResponseDate(LocalDate.now());
//        request.set
        request.setManagerComment("Cancelled by employee");
        LeaveRequest savedRequest = leaveRequestRepo.save(request);

        if (workflowRequest != null) {
            workflowRequest.setStatus("CANCELLED"); // Engine/Listener will see this
            Request savedWorkflowRequest = requestRepository.save(workflowRequest);
        }
        else{
            log.error("Could not find Workflow Request for cancelled Leave Request {}. Balance reversal might be missed.", leaveId);
        }
        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear());


        return leaveRequestRepo.save(request);
    }

    /**
     * Validate manager permissions for an employee
     */
    private boolean validateManagerPermissions(String managerId, String employeeId) {
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        return employee != null &&
                employee.getManager() != null &&
                employee.getManager().getEmployeeId().equals(managerId);
    }

    /**
     * Get overlapping leave requests
     */
    @Override
    public List<LeaveRequest> getOverlappingRequests(String employeeId, LocalDate startDate, LocalDate endDate) {
        return leaveRequestRepo.findOverlappingLeaves(employeeId, startDate, endDate);
    }

    @Override
    public List<LeaveRequest> getLeaveHistoryByYear(String employeeId, LocalDate startDate, LocalDate endDate) {
        return leaveRequestRepo.findLeaveHistory(employeeId, startDate, endDate);
    }

    // ==================== NEW DTO-BASED MANAGER OPERATIONS ====================

    /**
     * Get leave requests for a manager based on query DTO
     */
    @Override
    public List<LeaveRequest> getRequestsForManager(ManagerQueryDTO queryDTO) {
        return leaveRequestRepo.findManagerRequestsByCriteria(queryDTO);
    }

    /**
     * Get leave history for a manager based on query DTO
     */
    @Override
    public List<LeaveRequest> getLeaveHistoryForManager(ManagerQueryDTO queryDTO) {
        return leaveRequestRepo.findManagerHistoryByCriteria(queryDTO);
    }

    /**
     * Approve a leave request using DTO
     */
//    @Override
//    @Transactional
//    public LeaveRequest approveRequest(ApprovalRequestDTO approvalRequest) {
//        // Find the leave request and validate manager permissions
//        LeaveRequest request = leaveRequestRepo
//                .findByLeaveIdAndEmployee_Manager_EmployeeId(approvalRequest.getLeaveId(), approvalRequest.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + approvalRequest.getLeaveId() + " for this manager"));
//
//        Employee manager = employeeRepo.findById(approvalRequest.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + approvalRequest.getManagerId()));
//
//        // Update leave request status
//        request.setStatus(LeaveStatus.APPROVED);
//        request.setApprovedBy(manager);
//        request.setResponseDate(LocalDate.now());
//
//        // Add manager comment if provided
//        if (approvalRequest.getComment() != null && !approvalRequest.getComment().trim().isEmpty()) {
//            request.setManagerComment(approvalRequest.getComment());
//        }
//
//        // Save the updated request
//        LeaveRequest approvedRequest = leaveRequestRepo.save(request);
//
//        // Send email notification to employee
//        try {
//            if (request.getEmployee().getEmail() != null) {
//                emailService.sendLeaveApprovalNotification(
//                        request.getEmployee().getEmail(),
//                        request.getEmployee().getFullName(),
//                        request.getLeaveType().getLeaveName(),
//                        request.getStartDate().toString(),
//                        request.getEndDate().toString(),
//                        approvalRequest.getComment()
//                );
//            }
//        } catch (Exception e) {
//            // Log the error but don't fail the request
//            System.err.println("Failed to send approval email: " + e.getMessage());
//        }
//
//        return approvedRequest;
//    }

    /**
     * Approve multiple leave requests using DTO
     */

//    @Transactional
//    public List<LeaveRequest> approveMultipleRequests(BatchApprovalRequestDTO batchApproval) {
//        String managerId = batchApproval.getManagerId();
//
//        Employee manager = employeeRepo.findById(managerId)
//                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + managerId));
//
//        List<LeaveRequest> approvedRequests = new ArrayList<>();
//
//        for (String leaveId : batchApproval.getLeaveIds()) {
//            LeaveRequest request = leaveRequestRepo
//                    .findByLeaveIdAndEmployee_Manager_EmployeeId(leaveId, managerId)
//                    .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId + " for this manager"));
//
//            request.setStatus(LeaveStatus.APPROVED);
//            request.setApprovedBy(manager);
//            request.setResponseDate(LocalDate.now());
//
//            approvedRequests.add(request);
//        }
//
//        return leaveRequestRepo.saveAll(approvedRequests);
//    }


//    @Transactional
//    public List<LeaveRequest> rejectMultipleRequests(BatchApprovalRequestDTO batchApproval) {
//        String managerId = batchApproval.getManagerId();
//
//        Employee manager = employeeRepo.findById(managerId)
//                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + managerId));
//
//        List<LeaveRequest> approvedRequests = new ArrayList<>();
//
//        for (String leaveId : batchApproval.getLeaveIds()) {
//            LeaveRequest request = leaveRequestRepo
//                    .findByLeaveIdAndEmployee_Manager_EmployeeId(leaveId, managerId)
//                    .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId + " for this manager"));
//
//            request.setStatus(LeaveStatus.REJECTED);
//            request.setApprovedBy(manager);
//            request.setResponseDate(LocalDate.now());
//
//            approvedRequests.add(request);
//
//            leaveBalanceService.updateLeaveBalanceAfterRejected(
//                    request.getEmployee().getEmployeeId(),
//                    request.getLeaveType().getLeaveTypeId(),
//                    request.getDaysRequested(),
//                    request.getRequestDate().getYear());
//        }
//
//        return leaveRequestRepo.saveAll(approvedRequests);
//    }

    @Override
    public List<PendingAndApprovedLeaveRequestsDTO> getPendingLeaveAndApprovedLeaveByEmployeeId(String employeeId, LocalDate startDate, LocalDate endDate) {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findPendingOrApprovedByEmployee(employeeId);
        if(leaveRequests.isEmpty()){
            return Collections.emptyList();
        }
        return leaveRequests.stream()
                .filter(l -> (l.getStartDate().isEqual(startDate) || l.getStartDate().isAfter(startDate)|| l.getStartDate().isBefore(startDate))
                    && (l.getEndDate().equals(endDate) || l.getEndDate().isBefore(endDate) || l.getEndDate().isAfter(endDate))
                ).map(l -> new PendingAndApprovedLeaveRequestsDTO(
                        l.getEmployee().getEmployeeId(),
                        l.getEmployee().getFirstName()+" "+l.getEmployee().getLastName(),
                        l.getStartDate(),
                        l.getEndDate(),
                        l.getStatus().toString()
                )).toList();
    }


    /**
     * Reject a leave request using DTO
     */
//    @Override
//    @Transactional
//    public LeaveRequest rejectRequest(RejectionRequestDTO rejectionRequest) {
//        // Find the leave request and validate manager permissions
//        LeaveRequest request = leaveRequestRepo
//                .findByLeaveIdAndEmployee_Manager_EmployeeId(rejectionRequest.getLeaveId(), rejectionRequest.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + rejectionRequest.getLeaveId() + " for this manager"));
//
//        Employee manager = employeeRepo.findById(rejectionRequest.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + rejectionRequest.getManagerId()));
//
//        if(request.getStatus() == LeaveStatus.APPROVED){
//            request.setStatus(LeaveStatus.CANCELLED);
//        }else{
//            request.setStatus(LeaveStatus.REJECTED);
//        }
//        request.setApprovedBy(manager);
//        request.setResponseDate(LocalDate.now());
//        request.setManagerComment(rejectionRequest.getComment());
//
//        // Save the updated request
//        LeaveRequest rejectedRequest = leaveRequestRepo.save(request);
//
//        // Update leave balance to return the days
//        leaveBalanceService.updateLeaveBalanceAfterRejected(
//                request.getEmployee().getEmployeeId(),
//                request.getLeaveType().getLeaveTypeId(),
//                request.getDaysRequested(),
//                request.getRequestDate().getYear());
//
//        // Send email notification to employee
//        try {
//            if (request.getEmployee().getEmail() != null) {
//                emailService.sendLeaveRejectionNotification(
//                        request.getEmployee().getEmail(),
//                        request.getEmployee().getFullName(),
//                        request.getLeaveType().getLeaveName(),
//                        request.getStartDate().toString(),
//                        request.getEndDate().toString(),
//                        rejectionRequest.getComment()
//                );
//            }
//        } catch (Exception e) {
//            // Log the error but don't fail the request
//            System.err.println("Failed to send rejection email: " + e.getMessage());
//        }
//
//        return rejectedRequest;
//    }

    /**
     * Update a leave request by manager using DTO
     */
//    @Override
//    @Transactional
//    public LeaveRequest updateLeaveRequestByManager(ManagerUpdateRequestDTO updateRequest) {
//        // Fetch the leave request assigned to the manager
//        LeaveRequest request = leaveRequestRepo
//                .findByLeaveIdAndEmployee_Manager_EmployeeId(updateRequest.getLeaveId(), updateRequest.getManagerId())
//                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + updateRequest.getLeaveId() + " for this manager"));
//
//        leaveBalanceService.updateLeaveBalanceAfterRejected(
//                request.getEmployee().getEmployeeId(),
//                request.getLeaveType().getLeaveTypeId(),
//                request.getDaysRequested(),
//                request.getRequestDate().getYear()
//        );
//
//        // Prepare new values (use updated if provided, else fallback to existing)
//        LeaveType updatedLeaveType = request.getLeaveType();
//        if (updateRequest.getLeaveTypeId() != null) {
//            updatedLeaveType = leaveTypeRepo.findById(updateRequest.getLeaveTypeId())
//                    .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + updateRequest.getLeaveTypeId()));
//        }
//
//        // Build validation DTO before applying changes
////        LeaveRequestValidationDTO validationDTO = LeaveRequestValidationDTO.builder()
////                .leaveId(request.getLeaveId())
////                .employeeId(request.getEmployee().getEmployeeId())
////                .leaveTypeId(updatedLeaveType.getLeaveTypeId())
////                .startDate(updateRequest.getStartDate())
////                .endDate(updateRequest.getEndDate())
////                .daysRequested(updateRequest.getDaysRequested())
////                .reason(updateRequest.getReason())
////                .driveLink(updateRequest.getDriveLink())
////                .build();
//        // Build a complete validation DTO by merging new and old data
//        LeaveRequestValidationDTO validationDTO = LeaveRequestValidationDTO.builder()
//                .leaveId(request.getLeaveId())
//                .employeeId(request.getEmployee().getEmployeeId())
////                .leaveTypeId(updatedLeaveType.getLeaveTypeId())
//                .leaveTypeId(updateRequest.getLeaveTypeId() != null ? updateRequest.getLeaveTypeId():updatedLeaveType.getLeaveTypeId())
//
//                // Use the new start date if provided, otherwise keep the old one
//                .startDate(updateRequest.getStartDate() != null ? updateRequest.getStartDate() : request.getStartDate())
//
//                // Use the new end date if provided, otherwise keep the old one
//                .endDate(updateRequest.getEndDate() != null ? updateRequest.getEndDate() : request.getEndDate())
//
//                // Use new days if provided, otherwise keep the old ones
//                .daysRequested(updateRequest.getDaysRequested() != null ? updateRequest.getDaysRequested() : request.getDaysRequested())
//
//                // Use the new reason if provided, otherwise keep the old one
//                .reason(updateRequest.getReason() != null ? updateRequest.getReason() : request.getReason())
//
//                .driveLink(updateRequest.getDriveLink() != null ? updateRequest.getDriveLink() : request.getDriveLink())
//                .requestDate(request.getRequestDate())
//                .build();
//
//        // Validate proposed update
//        ValidationResultDTO validationResult = validateLeaveRequest(validationDTO);
//        if (!validationResult.isValid()) {
//            throw new RuntimeException("Validation failed: " + String.join(", ", validationResult.getErrors()));
//        }
//
//        // Track changes for notification
////        StringBuilder changes = new StringBuilder();
////        if (!updatedLeaveType.getLeaveTypeId().equals(request.getLeaveType().getLeaveTypeId())) {
////            changes.append("Leave Type: ").append(request.getLeaveType().getLeaveName())
////                    .append(" → ").append(updatedLeaveType.getLeaveName()).append("\n");
////            request.setLeaveType(updatedLeaveType);
////        }
//        StringBuilder changes = new StringBuilder();
//
//        // 1. Track and apply LeaveType update
//        if (updateRequest.getLeaveTypeId() != null && !updatedLeaveType.getLeaveTypeId().equals(request.getLeaveType().getLeaveTypeId())) {
//            changes.append("Leave Type: ").append(request.getLeaveType().getLeaveName())
//                    .append(" → ").append(updatedLeaveType.getLeaveName()).append("\n");
//            request.setLeaveType(updatedLeaveType);
//        }
//
//        // 2. Track and apply StartDate update
//        if (updateRequest.getStartDate() != null && !updateRequest.getStartDate().equals(request.getStartDate())) {
//            changes.append("Start Date: ").append(request.getStartDate())
//                    .append(" → ").append(updateRequest.getStartDate()).append("\n");
//            request.setStartDate(updateRequest.getStartDate());
//        }
//
//        // 3. Track and apply EndDate update
//        if (updateRequest.getEndDate() != null && !updateRequest.getEndDate().equals(request.getEndDate())) {
//            changes.append("End Date: ").append(request.getEndDate())
//                    .append(" → ").append(updateRequest.getEndDate()).append("\n");
//            request.setEndDate(updateRequest.getEndDate());
//        }
//
//        // 4. Track and apply DaysRequested update
//        if (updateRequest.getDaysRequested() != null && !updateRequest.getDaysRequested().equals(request.getDaysRequested())) {
//            changes.append("Days Requested: ").append(request.getDaysRequested())
//                    .append(" → ").append(updateRequest.getDaysRequested()).append("\n");
//            request.setDaysRequested(updateRequest.getDaysRequested());
//        }
//
//        // 5. Track and apply Reason update
//        if (updateRequest.getReason() != null && !updateRequest.getReason().equals(request.getReason())) {
//            changes.append("Reason has been updated.\n");
//            request.setReason(updateRequest.getReason());
//        }
//
//        leaveBalanceService.updateLeaveBalanceAfterApproval(
//                request.getEmployee().getEmployeeId(),
//                updateRequest.getLeaveTypeId(),
//                updateRequest.getDaysRequested(),
//                request.getRequestDate().getYear()
//        );
//        request.setStartSession(updateRequest.getStartSession());
//        request.setEndSession(updateRequest.getEndSession());
//        // Save only if there are changes
//        LeaveRequest updatedRequest = leaveRequestRepo.save(request);
//
//        // Send email if there were updates
//        if (changes.length() > 0) {
//            try {
//                String email = request.getEmployee().getEmail();
//                if (email != null) {
//                    emailService.sendLeaveUpdateNotification(
//                            email,
//                            request.getEmployee().getFullName(),
//                            request.getLeaveType().getLeaveName(),
//                            request.getStartDate().toString(),
//                            request.getEndDate().toString(),
//                            changes.toString()
//                    );
//                }
//            } catch (Exception e) {
//                System.err.println("Failed to send update notification email: " + e.getMessage());
//            }
//        }
//
//        return updatedRequest;
//    }


    // ==================== SMART UPDATE WITH LEVEL PRESERVATION ====================

    /**
     * Assesses the impact of changes between original and updated leave request.
     * Determines whether changes are MAJOR (requires workflow reset),
     * MINOR (preserve approvals, notify), or TRIVIAL (no workflow impact).
     */
    private LeaveChangeDetails assessChangeImpact(LeaveRequest original, LeaveRequestValidationDTO updated) {
        LeaveChangeDetails changeDetails = LeaveChangeDetails.builder()
                .updatedBy(original.getEmployee().getEmployeeId())
                .build();
        
        // 1. Check leave type change
        if (!original.getLeaveType().getLeaveTypeId().equals(updated.getLeaveTypeId())) {
            changeDetails.setLeaveTypeChanged(true);
            changeDetails.addChange("Leave type changed from " + 
                original.getLeaveType().getLeaveName() + " to new type");
        }
        
        // 2. Check date changes
        boolean startDateChanged = !original.getStartDate().equals(updated.getStartDate());
        boolean endDateChanged = !original.getEndDate().equals(updated.getEndDate());
        if (startDateChanged || endDateChanged) {
            changeDetails.setDatesChanged(true);
            if (startDateChanged) {
                changeDetails.addChange("Start date: " + original.getStartDate() + " → " + updated.getStartDate());
            }
            if (endDateChanged) {
                changeDetails.addChange("End date: " + original.getEndDate() + " → " + updated.getEndDate());
            }
        }
        
        // 3. Check duration change
        double originalDays = original.getDaysRequested();
        double updatedDays = updated.getDaysRequested();
        if (Math.abs(originalDays - updatedDays) > 0.01) { // Allow for floating point precision
            changeDetails.setDurationChanged(true);
            changeDetails.setDaysDifference((int) Math.abs(originalDays - updatedDays));
            changeDetails.addChange("Duration: " + originalDays + " days → " + updatedDays + " days");
        }
        
        // 4. Check reason change
        if (!Objects.equals(original.getReason(), updated.getReason())) {
            changeDetails.setReasonChanged(true);
            changeDetails.addChange("Reason updated");
        }
        
        // 5. Check documentation change
        if (!Objects.equals(original.getDriveLink(), updated.getDriveLink())) {
            changeDetails.setDocumentationChanged(true);
            changeDetails.addChange("Documentation link updated");
        }
        
        // 6. Determine impact level based on changes
        ChangeImpact impact = determineImpactLevel(changeDetails);
        changeDetails.setImpact(impact);
        
        log.info("Change assessment for Leave Request {}: Impact={}, Changes={}", 
            original.getLeaveId(), impact, changeDetails.getChanges());
        
        return changeDetails;
    }
    
    /**
     * Determines the impact level based on change details.
     * 
     * MAJOR: Leave type change, duration change >2 days, or significant date shifts
     * MINOR: Duration change ≤2 days, date changes within reason, reason updates
     * TRIVIAL: Only documentation or minor formatting changes
     */
    private ChangeImpact determineImpactLevel(LeaveChangeDetails changes) {
        // MAJOR changes that require complete workflow reset
        if (changes.isLeaveTypeChanged()) {
            return ChangeImpact.MAJOR;
        }
        
        if (changes.isDurationChanged() && changes.getDaysDifference() > 2) {
            return ChangeImpact.MAJOR;
        }
        
        // Check if dates changed by more than 3 days (indicates major reschedule)
        if (changes.isDatesChanged() && changes.isDurationChanged() && changes.getDaysDifference() > 3) {
            return ChangeImpact.MAJOR;
        }
        
        // MINOR changes that preserve workflow but require notification
        if (changes.isDatesChanged() || changes.isDurationChanged() || changes.isReasonChanged()) {
            return ChangeImpact.MINOR;
        }
        
        // TRIVIAL changes - only documentation or session changes
        if (changes.isDocumentationChanged()) {
            return ChangeImpact.TRIVIAL;
        }
        
        // No changes detected
        return ChangeImpact.TRIVIAL;
    }

    // update request by employee(using workflow engine)
    @Override
    @Transactional // Ensure the entire operation is atomic
    public ValidationResultDTO updateRequestByEmployee(LeaveRequest originalLeaveRequest, LeaveRequestValidationDTO updatedDetailsDto) {

        log.info("Attempting to update Leave Request {} by employee {}",
                originalLeaveRequest.getLeaveId(), originalLeaveRequest.getEmployee().getEmployeeId());

        // --- 1. Find the associated workflow Request ---
        Request workflowRequest = requestRepository.findByTargetEntityId(originalLeaveRequest.getLeaveId())
                .orElse(null); // Find the workflow linked to this leave

        // --- 2. Check Workflow Status ---
        if (workflowRequest == null) {
            log.error("Cannot update Leave Request {}: Corresponding workflow Request not found.", originalLeaveRequest.getLeaveId());
            // Handle this case - maybe the workflow never started or was deleted?
            // Depending on policy, you might allow update if LeaveRequest status is still PENDING_APPROVAL
            if (originalLeaveRequest.getStatus() != LeaveStatus.PENDING_APPROVAL) {
                throw new IllegalStateException("Cannot update: Workflow not found and Leave Request is not in a pending state.");
            }
            log.warn("Workflow Request not found for Leave Request {}. Proceeding with update based on LeaveRequest status.", originalLeaveRequest.getLeaveId());

        } else if (!"PENDING".equals(workflowRequest.getStatus())) {
            log.warn("Cannot update Leave Request {}: Workflow {} is already finalized with status {}.",
                    originalLeaveRequest.getLeaveId(), workflowRequest.getId(), workflowRequest.getStatus());
            throw new IllegalStateException("Cannot update a leave request once the approval process is complete or rejected.");
        }

        // --- 3. Reverse the Original Balance Deduction ---
        log.debug("Reversing original balance deduction for Leave Request {}", originalLeaveRequest.getLeaveId());
        try {
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    originalLeaveRequest.getEmployee().getEmployeeId(),
                    originalLeaveRequest.getLeaveType().getLeaveTypeId(),
                    originalLeaveRequest.getDaysRequested(),
                    originalLeaveRequest.getRequestDate().getYear()
            );
        } catch (Exception e) {
            log.error("CRITICAL: Failed to reverse original balance deduction for update of Leave Request {}. Rolling back.",
                    originalLeaveRequest.getLeaveId(), e);
            throw new RuntimeException("Failed to reverse original balance. Update aborted.", e);
        }

        // --- 4. Perform Validation on the NEW Details ---
        // Ensure the DTO has the leaveId set for conflict checking exclusion
        updatedDetailsDto.setLeaveId(originalLeaveRequest.getLeaveId());
        ValidationResultDTO validationResult = validateLeaveRequest(updatedDetailsDto);
        if (!validationResult.isValid()) {
            log.warn("Validation failed for updated Leave Request {}: {}",
                    originalLeaveRequest.getLeaveId(), validationResult.getErrors());
            // CRITICAL: We reversed the balance but cannot proceed. The transaction *must* roll back.
            throw new RuntimeException("Validation failed for updated details: " + String.join("; ", validationResult.getErrors()));
        }

        // --- 5. Cancel the Old Workflow (if it exists) ---
        if (workflowRequest != null) {
            log.info("Cancelling old workflow Request {}", workflowRequest.getId());
            workflowRequest.setStatus("CANCELLED");
            requestRepository.save(workflowRequest);

            // Cancel associated pending/waiting stages
            List<ApprovalStage> activeStages = approvalStageRepository.findByRequestIdAndStatusIn(
                    workflowRequest.getId(), List.of("PENDING", "WAITING")
            );
            activeStages.forEach(stage -> stage.setStatus("CANCELLED"));
            approvalStageRepository.saveAll(activeStages);

            // Publish event so listener can handle any side effects (like reversing balance if flow was different)
            // Since we handle balance reversal directly here, this might just be for logging/audit.
            eventPublisher.publishEvent(new WorkflowCompletionEvent(this, workflowRequest));
        }

        // --- 6. Update the LeaveRequest Entity with New Details ---
        log.debug("Updating LeaveRequest entity {} with new details.", originalLeaveRequest.getLeaveId());
        LeaveType newLeaveType = leaveTypeRepo.findById(updatedDetailsDto.getLeaveTypeId())
                .orElseThrow(() -> new RuntimeException("Leave type not found: " + updatedDetailsDto.getLeaveTypeId()));

        originalLeaveRequest.setLeaveType(newLeaveType);
        originalLeaveRequest.setStartDate(updatedDetailsDto.getStartDate());
        originalLeaveRequest.setEndDate(updatedDetailsDto.getEndDate());
        originalLeaveRequest.setDaysRequested(updatedDetailsDto.getDaysRequested());
        originalLeaveRequest.setReason(updatedDetailsDto.getReason());
        originalLeaveRequest.setDriveLink(updatedDetailsDto.getDriveLink());
        originalLeaveRequest.setStartSession(updatedDetailsDto.getStartSession());
        originalLeaveRequest.setEndSession(updatedDetailsDto.getEndSession());
        originalLeaveRequest.setStatus(LeaveStatus.PENDING_APPROVAL); // Reset status
        originalLeaveRequest.setApprovedBy(null); // Clear previous approval info
        originalLeaveRequest.setResponseDate(null);
        originalLeaveRequest.setManagerComment(null);
        // requestDate likely remains the original submission date

        LeaveRequest updatedLeaveRequest = leaveRequestRepo.save(originalLeaveRequest);

        // --- 7. Deduct Balance for the NEW Request ---
        log.debug("Deducting new balance for updated Leave Request {}", updatedLeaveRequest.getLeaveId());
        try {
            leaveBalanceService.updateLeaveBalanceAfterApproval( // Using your deduction method
                    updatedLeaveRequest.getEmployee().getEmployeeId(),
                    updatedLeaveRequest.getLeaveType().getLeaveTypeId(),
                    updatedLeaveRequest.getDaysRequested(),
                    updatedLeaveRequest.getRequestDate().getYear()
            );
        } catch (Exception e) {
            log.error("CRITICAL: Failed to deduct NEW balance after updating Leave Request {}. Rolling back.",
                    updatedLeaveRequest.getLeaveId(), e);
            // Transaction rollback is essential here.
            throw new RuntimeException("Failed to deduct new balance. Update aborted.", e);
        }

        // --- 8. Start a NEW Workflow Instance ---
        log.info("Starting new workflow for updated Leave Request {}", updatedLeaveRequest.getLeaveId());
        String makerAttributes = buildMakerAttributesJson(updatedLeaveRequest.getEmployee());

        Request newWorkflowRequest = Request.builder()
                .createdBy(updatedLeaveRequest.getEmployee().getEmployeeId())
                .requestType("LEAVE")
                .operationType("APPLY") // Or maybe "UPDATE"? Using APPLY restarts the flow.
                .status("PENDING")
                .targetEntityId(updatedLeaveRequest.getLeaveId()) // Link to the SAME updated LeaveRequest
                .leaveType(updatedLeaveRequest.getLeaveType().getLeaveTypeId())
                .totalDays((int) updatedLeaveRequest.getDaysRequested())
                .makerAttributes(makerAttributes)
                .build();
        Request savedNewWorkflowRequest = requestRepository.save(newWorkflowRequest);

        RuleSet matchedRule = ruleEvaluatorService.evaluate(savedNewWorkflowRequest)
                .orElseThrow(() -> {
                    log.error("No matching approval RuleSet found for updated Leave Request {}. Rolling back.", updatedLeaveRequest.getLeaveId());
                    return new RuntimeException("Configuration Error: No matching approval rule found for the updated leave request.");
                });
        workflowEngine.startWorkflow(savedNewWorkflowRequest, matchedRule);

        log.info("Successfully updated Leave Request {} and started new workflow {}.",
                updatedLeaveRequest.getLeaveId(), savedNewWorkflowRequest.getId());

        // Return the validation result (which should be valid) with a success message
        validationResult.addMessage("Leave request updated successfully and resubmitted for approval.");
        validationResult.setLeaveId(updatedLeaveRequest.getLeaveId()); // Ensure Leave ID is set
        return validationResult;
    }

    @Override
    public List<LeaveRequest> leaveBalanceViewDetails(String employeeId, String leaveName, Integer year){
        return leaveRequestRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveNameAndYear(employeeId, leaveName, year).stream().filter(obj -> obj.getStatus().equals(LeaveStatus.APPROVED) || obj.getStatus().equals(LeaveStatus.PENDING)).toList();
    }
}
