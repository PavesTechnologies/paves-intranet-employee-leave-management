package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
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
        if (leaveType.getRequiresDocumentation() && !leaveType.getLeaveTypeId().equals("L-SL")) {
            // For leave types that require documentation, drive link should be provided
            if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                result.addError("Drive link with supporting documents is required for " + leaveType.getLeaveName());
            } else {
                // Validate drive link format (basic URL validation)
                validateDriveLinkFormat(request.getDriveLink(), result);
            }
        }

        // For sick leave specifically, check if drive link is required for longer durations
        if ("Sick Leave".equalsIgnoreCase(leaveType.getLeaveName()) && request.getDaysRequested() > 3) {
            if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                result.addError("Drive link with medical certificate is mandatory for sick leave exceeding 3 days");
            }
        }
    }

    /**
     * Validates the format of the drive link URL
     */
    private void validateDriveLinkFormat(String driveLink, ValidationResultDTO result) {
        if (driveLink != null && !driveLink.trim().isEmpty()) {
            String trimmedLink = driveLink.trim();

            // Basic URL format validation
            if (!trimmedLink.startsWith("http://") && !trimmedLink.startsWith("https://")) {
                result.addError("Drive link must be a valid URL starting with http:// or https://");
                return;
            }

            // Check if it's a Google Drive link (optional - can be any cloud storage)
            if (trimmedLink.contains("drive.google.com") || trimmedLink.contains("docs.google.com")) {
                // Additional validation for Google Drive links if needed
                if (!trimmedLink.contains("/") || trimmedLink.length() < 20) {
                    result.addError("Invalid Google Drive link format");
                }
            }
        }
    }

    /**
     * Validates basic date constraints that apply to ALL leave types
     */
    private void validateBasicDateConstraints(LeaveRequestValidationDTO request, ValidationResultDTO result) {
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

            return savedRequest;
        } else {
            return null;
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

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new RuntimeException("Cannot cancel a leave request that is not pending");
        }

        request.setStatus(LeaveStatus.CANCELLED);
        request.setResponseDate(LocalDate.now());
//        request.set
//        request.setManagerComment("Cancelled by employee");

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
    @Override
    @Transactional
    public LeaveRequest approveRequest(ApprovalRequestDTO approvalRequest) {
        // Find the leave request and validate manager permissions
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(approvalRequest.getLeaveId(), approvalRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + approvalRequest.getLeaveId() + " for this manager"));

        Employee manager = employeeRepo.findById(approvalRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + approvalRequest.getManagerId()));

        // Update leave request status
        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());

        // Add manager comment if provided
        if (approvalRequest.getComment() != null && !approvalRequest.getComment().trim().isEmpty()) {
            request.setManagerComment(approvalRequest.getComment());
        }

        // Save the updated request
        LeaveRequest approvedRequest = leaveRequestRepo.save(request);

        // Send email notification to employee
        try {
            if (request.getEmployee().getEmail() != null) {
                emailService.sendLeaveApprovalNotification(
                        request.getEmployee().getEmail(),
                        request.getEmployee().getFullName(),
                        request.getLeaveType().getLeaveName(),
                        request.getStartDate().toString(),
                        request.getEndDate().toString(),
                        approvalRequest.getComment()
                );
            }
        } catch (Exception e) {
            // Log the error but don't fail the request
            System.err.println("Failed to send approval email: " + e.getMessage());
        }

        return approvedRequest;
    }

    /**
     * Approve multiple leave requests using DTO
     */

    @Transactional
    public List<LeaveRequest> approveMultipleRequests(BatchApprovalRequestDTO batchApproval) {
        String managerId = batchApproval.getManagerId();

        Employee manager = employeeRepo.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + managerId));

        List<LeaveRequest> approvedRequests = new ArrayList<>();

        for (String leaveId : batchApproval.getLeaveIds()) {
            LeaveRequest request = leaveRequestRepo
                    .findByLeaveIdAndEmployee_Manager_EmployeeId(leaveId, managerId)
                    .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId + " for this manager"));

            request.setStatus(LeaveStatus.APPROVED);
            request.setApprovedBy(manager);
            request.setResponseDate(LocalDate.now());

            approvedRequests.add(request);
        }

        return leaveRequestRepo.saveAll(approvedRequests);
    }


    @Transactional
    public List<LeaveRequest> rejectMultipleRequests(BatchApprovalRequestDTO batchApproval) {
        String managerId = batchApproval.getManagerId();

        Employee manager = employeeRepo.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + managerId));

        List<LeaveRequest> approvedRequests = new ArrayList<>();

        for (String leaveId : batchApproval.getLeaveIds()) {
            LeaveRequest request = leaveRequestRepo
                    .findByLeaveIdAndEmployee_Manager_EmployeeId(leaveId, managerId)
                    .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId + " for this manager"));

            request.setStatus(LeaveStatus.REJECTED);
            request.setApprovedBy(manager);
            request.setResponseDate(LocalDate.now());

            approvedRequests.add(request);

            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear());
        }

        return leaveRequestRepo.saveAll(approvedRequests);
    }


    /**
     * Reject a leave request using DTO
     */
    @Override
    @Transactional
    public LeaveRequest rejectRequest(RejectionRequestDTO rejectionRequest) {
        // Find the leave request and validate manager permissions
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(rejectionRequest.getLeaveId(), rejectionRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + rejectionRequest.getLeaveId() + " for this manager"));

        Employee manager = employeeRepo.findById(rejectionRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + rejectionRequest.getManagerId()));

        request.setStatus(LeaveStatus.REJECTED);
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());
        request.setManagerComment(rejectionRequest.getComment());

        // Save the updated request
        LeaveRequest rejectedRequest = leaveRequestRepo.save(request);

        // Update leave balance to return the days
        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear());

        // Send email notification to employee
        try {
            if (request.getEmployee().getEmail() != null) {
                emailService.sendLeaveRejectionNotification(
                        request.getEmployee().getEmail(),
                        request.getEmployee().getFullName(),
                        request.getLeaveType().getLeaveName(),
                        request.getStartDate().toString(),
                        request.getEndDate().toString(),
                        rejectionRequest.getComment()
                );
            }
        } catch (Exception e) {
            // Log the error but don't fail the request
            System.err.println("Failed to send rejection email: " + e.getMessage());
        }

        return rejectedRequest;
    }

    /**
     * Update a leave request by manager using DTO
     */
    @Override
    @Transactional
    public LeaveRequest updateLeaveRequestByManager(ManagerUpdateRequestDTO updateRequest) {
        // Fetch the leave request assigned to the manager
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(updateRequest.getLeaveId(), updateRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + updateRequest.getLeaveId() + " for this manager"));

        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear()
        );

        // Prepare new values (use updated if provided, else fallback to existing)
        LeaveType updatedLeaveType = request.getLeaveType();
        if (updateRequest.getLeaveTypeId() != null) {
            updatedLeaveType = leaveTypeRepo.findById(updateRequest.getLeaveTypeId())
                    .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + updateRequest.getLeaveTypeId()));
        }

        // Build validation DTO before applying changes
        LeaveRequestValidationDTO validationDTO = LeaveRequestValidationDTO.builder()
                .leaveId(request.getLeaveId())
                .employeeId(request.getEmployee().getEmployeeId())
                .leaveTypeId(updatedLeaveType.getLeaveTypeId())
                .startDate(updateRequest.getStartDate())
                .endDate(updateRequest.getEndDate())
                .daysRequested(updateRequest.getDaysRequested())
                .reason(updateRequest.getReason())
                .driveLink(updateRequest.getDriveLink())
                .build();

        // Validate proposed update
        ValidationResultDTO validationResult = validateLeaveRequest(validationDTO);
        if (!validationResult.isValid()) {
            throw new RuntimeException("Validation failed: " + String.join(", ", validationResult.getErrors()));
        }

        // Track changes for notification
        StringBuilder changes = new StringBuilder();
        if (!updatedLeaveType.getLeaveTypeId().equals(request.getLeaveType().getLeaveTypeId())) {
            changes.append("Leave Type: ").append(request.getLeaveType().getLeaveName())
                    .append(" → ").append(updatedLeaveType.getLeaveName()).append("\n");
            request.setLeaveType(updatedLeaveType);
        }
        leaveBalanceService.updateLeaveBalanceAfterApproval(
                request.getEmployee().getEmployeeId(),
                updateRequest.getLeaveTypeId(),
                updateRequest.getDaysRequested(),
                updateRequest.getRequestDate().getYear()
        );

        // Save only if there are changes
        LeaveRequest updatedRequest = leaveRequestRepo.save(request);

        // Send email if there were updates
        if (changes.length() > 0) {
            try {
                String email = request.getEmployee().getEmail();
                if (email != null) {
                    emailService.sendLeaveUpdateNotification(
                            email,
                            request.getEmployee().getFullName(),
                            request.getLeaveType().getLeaveName(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString(),
                            changes.toString()
                    );
                }
            } catch (Exception e) {
                System.err.println("Failed to send update notification email: " + e.getMessage());
            }
        }

        return updatedRequest;
    }


    // ==================== UTILITY METHODS ====================

    /**
     * Get employee leave balance
     */
    @Override
    public LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        return leaveBalanceService.getLeaveBalance(employeeId, leaveTypeId, year);
    }

    /**
     * Update leave request by employee
     */
    @Override
    public ValidationResultDTO updateRequestByEmployee(LeaveRequest leaveRequest, LeaveRequestValidationDTO request) {
        return leaveRequestRepo.findByLeaveIdAndEmployee_EmployeeId(
                        leaveRequest.getLeaveId(), leaveRequest.getEmployee().getEmployeeId())
                .map(existingRequest -> {
                    // Disallow update if already approved/rejected
                    if (existingRequest.getStatus() == LeaveStatus.APPROVED ||
                            existingRequest.getStatus() == LeaveStatus.REJECTED) {
                        throw new LeaveBalanceExceptionHandler("Cannot update a leave request that has already been approved or rejected.");
                    }

                    // Reverse the old leave balance before applying changes
                    leaveBalanceService.updateLeaveBalanceAfterRejected(
                            existingRequest.getEmployee().getEmployeeId(),
                            existingRequest.getLeaveType().getLeaveTypeId(),
                            existingRequest.getDaysRequested(),
                            existingRequest.getRequestDate().getYear()
                    );

                    // Build new leave type
                    LeaveType updatedLeaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();
                    if (updatedLeaveType == null) {
                        throw new LeaveBalanceExceptionHandler("Leave type not found: " + request.getLeaveTypeId());
                    }

                    // Validate updated request
                    ValidationResultDTO validationResult = validateLeaveRequest(request);
                    if (!validationResult.isValid()) {
                        return validationResult;
                    }

                    // Track changes for email
                    StringBuilder changes = new StringBuilder();
                    if (!existingRequest.getLeaveType().getLeaveTypeId().equals(updatedLeaveType.getLeaveTypeId())) {
                        changes.append("Leave Type: ").append(existingRequest.getLeaveType().getLeaveName())
                                .append(" → ").append(updatedLeaveType.getLeaveName()).append("\n");
                    }
                    if (!existingRequest.getStartDate().equals(request.getStartDate())) {
                        changes.append("Start Date: ").append(existingRequest.getStartDate())
                                .append(" → ").append(request.getStartDate()).append("\n");
                    }
                    if (!existingRequest.getEndDate().equals(request.getEndDate())) {
                        changes.append("End Date: ").append(existingRequest.getEndDate())
                                .append(" → ").append(request.getEndDate()).append("\n");
                    }
                    if (existingRequest.getDaysRequested() != request.getDaysRequested()) {
                        changes.append("Days Requested: ").append(existingRequest.getDaysRequested())
                                .append(" → ").append(request.getDaysRequested()).append("\n");
                    }
                    if (!Objects.equals(existingRequest.getReason(), request.getReason())) {
                        changes.append("Reason updated.\n");
                    }

                    // Update fields
                    existingRequest.setLeaveType(updatedLeaveType);
                    existingRequest.setStartDate(request.getStartDate());
                    existingRequest.setEndDate(request.getEndDate());
                    existingRequest.setDaysRequested(request.getDaysRequested());
                    existingRequest.setReason(request.getReason());
                    existingRequest.setDriveLink(request.getDriveLink());

                    // Reset approval fields
                    existingRequest.setApprovedBy(null);
                    existingRequest.setResponseDate(null);
                    existingRequest.setManagerComment(null);
                    existingRequest.setStatus(LeaveStatus.PENDING);

                    // Re-apply updated leave balance
                    leaveBalanceService.updateLeaveBalanceAfterApproval(
                            request.getEmployeeId(),
                            request.getLeaveTypeId(),
                            request.getDaysRequested(),
                            request.getRequestDate().getYear()
                    );

                    // Save updated request
                    LeaveRequest updatedRequest = leaveRequestRepo.save(existingRequest);

                    // Notify manager if changes were made
                    if (changes.length() > 0) {
                        try {
                            String managerEmail = updatedRequest.getEmployee().getManager() != null ?
                                    updatedRequest.getEmployee().getManager().getEmail() : null;
                            if (managerEmail != null && !managerEmail.isEmpty()) {
                                emailService.sendLeaveUpdateNotification(
                                        managerEmail,
                                        updatedRequest.getEmployee().getFullName(),
                                        updatedLeaveType.getLeaveName(),
                                        updatedRequest.getStartDate().toString(),
                                        updatedRequest.getEndDate().toString(),
                                        changes.toString()
                                );
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to send update notification email: " + e.getMessage());
                        }
                    }

                    // Finalize result
                    validationResult.addMessage("Leave request updated successfully.");
                    validationResult.setLeaveId(updatedRequest.getLeaveId());

                    return validationResult;
                })
                .orElseThrow(() -> new LeaveBalanceExceptionHandler("Leave request not found for given ID and employee."));
    }
}
