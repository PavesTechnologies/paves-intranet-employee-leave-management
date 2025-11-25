package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeaveRequestService implements LeaveRequestServiceInterface {

    private static final Pattern GOOGLE_DRIVE_URL_PATTERN = Pattern.compile(
            "^https?://(drive|docs)\\.google\\.com/(file/d/|folders/|spreadsheets/d/|document/d/|open\\?id=)([a-zA-Z0-9_-]+)(/.*)?$"
    );
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

    @Override
    public ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO result = ValidationResultDTO.builder()
                .isValid(true)
                .employeeId(request.getEmployeeId())
                .requestedDays((float) request.getDaysRequested())
                .build();

        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();
        LeaveType leaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();

        if (!validateBasicRequirements(request, result, employee, leaveType)) {
            return result;
        }

        if (employee != null) {
            result.setEmployeeName(employee.getFullName());
        }

        validateBasicDateConstraints(request, result);
        validateLeaveBalance(request, result, employee, leaveType);
        validateLeaveConflicts(request, result);
        validateLeaveTypeSpecificRules(request, result, employee, leaveType);

        return result;
    }

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

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            result.addError("Leave reason/comments are mandatory");
        }

        validateDriveLinkRequirements(request, result, leaveType);

        return true;
    }

    private void validateDriveLinkRequirements(LeaveRequestValidationDTO request, ValidationResultDTO result, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && !leaveType.getLeaveTypeId().equals("L-SL")) {
            if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                result.addError("Drive link with supporting documents is required for " + leaveType.getLeaveName());
            } else {
                validateDriveLinkFormat(request.getDriveLink(), result);
            }
        }

        if (leaveType.getLeaveTypeId().equals("L-SL") && request.getDaysRequested() > 3) {
            if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                result.addError("Drive link with medical certificate is mandatory for sick leave exceeding 3 days");
            } else {
                validateDriveLinkFormat(request.getDriveLink(), result);
            }
        }
    }
    
    private void validateDriveLinkFormat(String driveLink, ValidationResultDTO result) {
        if (driveLink == null || driveLink.trim().isEmpty()) {
            result.addError("Drive link is required and cannot be empty.");
            return; 
        }

        String trimmedLink = driveLink.trim();

        if (!GOOGLE_DRIVE_URL_PATTERN.matcher(trimmedLink).matches()) {
            result.addError("Link must be a valid Google Drive URL format (e.g., drive.google.com/file/d/...).");
        }
    }

    private void validateBasicDateConstraints(LeaveRequestValidationDTO request, ValidationResultDTO result) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        int currentYear = LocalDate.now().getYear();

        if (endDate.isBefore(startDate)) {
            result.addError("End date must be after or equal to start date");
        }
        if (request.getStartDate().getYear() != LocalDate.now().getYear() && request.getLeaveTypeId().equals("L-SL")) {
            result.addError("Sick Leave request can only be made for the current year");
        }
        if (request.getLeaveTypeId().equals("L-EL") && startDate.getYear() > currentYear) {
            result.addError("Earned Leave cannot be applied in advance for the next year");
        }
    }

    private void validateLeaveBalance(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                      Employee employee, LeaveType leaveType) {
        Integer currentYear = LocalDate.now().getYear();
        LeaveBalanceDTO balance = leaveBalanceService.getLeaveBalance(
                request.getEmployeeId(), request.getLeaveTypeId(), currentYear);

        if (balance == null) {
            result.addError("Leave balance not found for the current year");
            return;
        }

        if (!leaveType.getLeaveTypeId().equals("L-UP") && !leaveType.getAllowNegativeBalance() &&
                balance.getRemainingLeaves() < request.getDaysRequested()) {
            result.addError(String.format(
                    "Insufficient %s balance. Available: %.2f days, Requested: %.2f days",
                    leaveType.getLeaveName(), balance.getRemainingLeaves(), request.getDaysRequested()));
        }

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

    private void validateLeaveTypeSpecificRules(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                                Employee employee, LeaveType leaveType) {
        String leaveTypeId = request.getLeaveTypeId();

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
                validateDefaultLeaveRules(request, result, employee, leaveType);
                break;
        }

        if (!leaveType.getAllowHalfDay() && request.getDaysRequested() < 1) {
            result.addError(String.format("%s does not allow half-day leave", leaveType.getLeaveName()));
        }
    }

    private void validateMaternityLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        int pendingCount = leaveRequestRepo.countPendingLeavesByType(employee.getEmployeeId(), "L-ML");
        if (pendingCount > 0) {
            result.addError("You already have a pending maternity leave request.");
            return;
        }

        List<LeaveRequest> approvedML = leaveRequestRepo.findApprovedLeavesByType(employee.getEmployeeId(), "L-ML");
        long longLeaves = approvedML.stream()
                .filter(lr -> lr.getDaysRequested() >= 48)
                .count();

        if (request.getDaysRequested() >= 48 && longLeaves >= 2) {
            result.addError("Maternity leave for 6 months (48+ days) can only be availed twice.");
            return;
        }

        if (request.getDaysRequested() >= 48 && request.getDaysRequested() != 180) {
            result.addError("Standard maternity leave should be exactly 180 days.");
        }

        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validatePaternityLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
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

        if (approvedPL.size() == 1) {
            LeaveRequest previousLeave = approvedPL.get(0);
            long gap = ChronoUnit.DAYS.between(previousLeave.getStartDate(), request.getStartDate());
            if (gap < 365) {
                result.addError("There must be a minimum 1-year gap between two paternity leaves.");
            }
        }

        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateCompensatoryLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            result.addError("Compensatory leave requires documentation/proof of overtime work");
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateSickLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && request.getDaysRequested() > 3 && request.getDriveLink() == null) {
            result.addError("Sick leave for more than 3 days requires medical certificate");
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateEarnedLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getWaitingPeriodDays() > 0) {
            LocalDate eligibilityDate = employee.getHireDate().plusDays(leaveType.getWaitingPeriodDays());
            if (LocalDate.now().isBefore(eligibilityDate)) {
                result.addError(String.format("Earned leave requires %d days of service before eligibility",
                        leaveType.getWaitingPeriodDays()));
            }
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateUnpaidLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            result.addError("Unpaid leave requires detailed justification");
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validateDefaultLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveType, result);
    }

    private void validatePastDateRestrictions(LocalDate startDate, LocalDate today, LeaveType leaveType, ValidationResultDTO result) {
        if (leaveType.getPastDateLimitDays() != null && leaveType.getPastDateLimitDays() > 0) {
            LocalDate pastLimit = today.minusDays(leaveType.getPastDateLimitDays());
            if (startDate.isBefore(pastLimit)) {
                result.addError(String.format("Cannot request leave more than %d days in the past",
                        leaveType.getPastDateLimitDays()));
            }
        } else if (startDate.isBefore(today.minusDays(1))) {
            result.addError("Cannot request leave for past dates");
        }
    }

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

    @Override
    @Transactional
    public LeaveRequest saveLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO validationResult = validateLeaveRequest(request);

        if (!validationResult.isValid()) {
            throw new RuntimeException("Leave request validation failed: " + String.join(", ", validationResult.getErrors()));
        }

        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();
        LeaveType leaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();

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
            leaveBalanceService.updateLeaveBalanceAfterApproval(
                    request.getEmployeeId(),
                    request.getLeaveTypeId(),
                    request.getDaysRequested(),
                    leaveRequest.getRequestDate().getYear()
            );

            if (employee.getManager() != null && employee.getManager().getEmail() != null) {
                Map<String, Object> templateModel = new LinkedHashMap<>();
                templateModel.put("title", "New Leave Application");
                templateModel.put("recipientName", employee.getManager().getFirstName());
                templateModel.put("messageBody", "A new leave application has been submitted by <strong>" + employee.getFullName() + "</strong>.");
                templateModel.put("detailsTitle", "Application Details");

                Map<String, String> details = new LinkedHashMap<>();
                details.put("Employee", employee.getFullName());
                details.put("Leave Type", leaveType.getLeaveName());
                details.put("Start Date", request.getStartDate().toString());
                details.put("End Date", request.getEndDate().toString());
                details.put("Reason", request.getReason());
                templateModel.put("details", details);

                templateModel.put("closingMessage", "Please review the application in the Leave Management System.");

                emailService.sendEmailFromTemplate(employee.getManager().getEmail(), "New Leave Application - " + employee.getFullName(), "generic-notification.html", templateModel);
            }
        }

        return savedRequest;
    }

    @Override
    public List<LeaveRequest> getLeaveRequestsByEmployee(String employeeId) {
        return leaveRequestRepo.findByEmployee_EmployeeId(employeeId);
    }

    @Override
    public LeaveRequest getLeaveRequestById(String leaveId) {
        return leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId));
    }

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

        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear());

        LeaveRequest cancelledRequest = leaveRequestRepo.save(request);

        // Notify manager
        Employee employee = cancelledRequest.getEmployee();
        Employee manager = employee.getManager();
        if (manager != null && manager.getEmail() != null && cancelledRequest != null) {
            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Leave Request Cancelled");
            templateModel.put("recipientName", manager.getFirstName());
            templateModel.put("messageBody", "A leave request from <strong>" + employee.getFullName() + "</strong> has been cancelled by the employee.");
            templateModel.put("detailsTitle", "Cancelled Request Details");

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Employee", employee.getFullName());
            details.put("Leave Type", cancelledRequest.getLeaveType().getLeaveName());
            details.put("Start Date", cancelledRequest.getStartDate().toString());
            details.put("End Date", cancelledRequest.getEndDate().toString());
            templateModel.put("details", details);

            emailService.sendEmailFromTemplate(manager.getEmail(), "Leave Request Cancelled - " + employee.getFullName(), "generic-notification.html", templateModel);
        }

        return cancelledRequest;
    }

    private boolean validateManagerPermissions(String managerId, String employeeId) {
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        return employee != null &&
                employee.getManager() != null &&
                employee.getManager().getEmployeeId().equals(managerId);
    }

    @Override
    public List<LeaveRequest> getOverlappingRequests(String employeeId, LocalDate startDate, LocalDate endDate) {
        return leaveRequestRepo.findOverlappingLeaves(employeeId, startDate, endDate);
    }

    @Override
    public List<LeaveRequest> getLeaveHistoryByYear(String employeeId, LocalDate startDate, LocalDate endDate) {
        return leaveRequestRepo.findLeaveHistory(employeeId, startDate, endDate);
    }

    @Override
    public List<LeaveRequest> getRequestsForManager(ManagerQueryDTO queryDTO) {
        return leaveRequestRepo.findManagerRequestsByCriteria(queryDTO);
    }

    @Override
    public List<LeaveRequest> getLeaveHistoryForManager(ManagerQueryDTO queryDTO) {
        return leaveRequestRepo.findManagerHistoryByCriteria(queryDTO);
    }

    @Override
    @Transactional
    public LeaveRequest approveRequest(ApprovalRequestDTO approvalRequest) {
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(approvalRequest.getLeaveId(), approvalRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + approvalRequest.getLeaveId() + " for this manager"));

        Employee manager = employeeRepo.findById(approvalRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + approvalRequest.getManagerId()));

        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());

        if (approvalRequest.getComment() != null && !approvalRequest.getComment().trim().isEmpty()) {
            request.setManagerComment(approvalRequest.getComment());
        }

        LeaveRequest approvedRequest = leaveRequestRepo.save(request);

        if (request.getEmployee().getEmail() != null && approvedRequest != null) {
            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Leave Application Approved");
            templateModel.put("recipientName", request.getEmployee().getFullName());
            templateModel.put("messageBody", "Your leave application for <strong>" + request.getLeaveType().getLeaveName() + "</strong> has been approved.");
            templateModel.put("detailsTitle", "Approval Details");

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Leave Type", request.getLeaveType().getLeaveName());
            details.put("Start Date", request.getStartDate().toString());
            details.put("End Date", request.getEndDate().toString());
            if (approvalRequest.getComment() != null) {
                details.put("Manager's Comment", approvalRequest.getComment());
            }
            templateModel.put("details", details);

            emailService.sendEmailFromTemplate(request.getEmployee().getEmail(), "Leave Application Approved", "generic-notification.html", templateModel);
        }

        return approvedRequest;
    }

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

        List<LeaveRequest> rejectedRequests = new ArrayList<>();
        for (String leaveId : batchApproval.getLeaveIds()) {
            LeaveRequest request = leaveRequestRepo
                    .findByLeaveIdAndEmployee_Manager_EmployeeId(leaveId, managerId)
                    .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId + " for this manager"));

            request.setStatus(LeaveStatus.REJECTED);
            request.setApprovedBy(manager);
            request.setResponseDate(LocalDate.now());
            rejectedRequests.add(request);

            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear());
        }
        return leaveRequestRepo.saveAll(rejectedRequests);
    }

    @Override
    public List<PendingAndApprovedLeaveRequestsDTO> getPendingLeaveAndApprovedLeaveByEmployeeId(String employeeId, LocalDate startDate, LocalDate endDate) {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findPendingOrApprovedByEmployee(employeeId);
        if (leaveRequests.isEmpty()) {
            return Collections.emptyList();
        }
        return leaveRequests.stream()
                .filter(l -> (l.getStartDate().isEqual(startDate) || l.getStartDate().isAfter(startDate) || l.getStartDate().isBefore(startDate))
                        && (l.getEndDate().equals(endDate) || l.getEndDate().isBefore(endDate) || l.getEndDate().isAfter(endDate))
                ).map(l -> new PendingAndApprovedLeaveRequestsDTO(
                        l.getEmployee().getEmployeeId(),
                        l.getEmployee().getFirstName() + " " + l.getEmployee().getLastName(),
                        l.getStartDate(),
                        l.getEndDate(),
                        l.getStatus().toString()
                )).toList();
    }

    @Override
    @Transactional
    public LeaveRequest rejectRequest(RejectionRequestDTO rejectionRequest) {
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(rejectionRequest.getLeaveId(), rejectionRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + rejectionRequest.getLeaveId() + " for this manager"));

        Employee manager = employeeRepo.findById(rejectionRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found with ID: " + rejectionRequest.getManagerId()));

        if (request.getStatus() == LeaveStatus.APPROVED) {
            request.setStatus(LeaveStatus.CANCELLED);
        } else {
            request.setStatus(LeaveStatus.REJECTED);
        }
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());
        request.setManagerComment(rejectionRequest.getComment());

        LeaveRequest rejectedRequest = leaveRequestRepo.save(request);

        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear());

        if (request.getEmployee().getEmail() != null) {
            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Leave Application Rejected");
            templateModel.put("recipientName", request.getEmployee().getFullName());
            templateModel.put("messageBody", "Your leave application for <strong>" + request.getLeaveType().getLeaveName() + "</strong> has been rejected.");
            templateModel.put("detailsTitle", "Rejection Details");

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Leave Type", request.getLeaveType().getLeaveName());
            details.put("Start Date", request.getStartDate().toString());
            details.put("End Date", request.getEndDate().toString());
            details.put("Rejection Reason", rejectionRequest.getComment());
            templateModel.put("details", details);

            emailService.sendEmailFromTemplate(request.getEmployee().getEmail(), "Leave Application Rejected", "generic-notification.html", templateModel);
        }

        return rejectedRequest;
    }

    @Override
    @Transactional
    public LeaveRequest updateLeaveRequestByManager(ManagerUpdateRequestDTO updateRequest) {
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(updateRequest.getLeaveId(), updateRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + updateRequest.getLeaveId() + " for this manager"));

        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear()
        );

        LeaveType updatedLeaveType = request.getLeaveType();
        if (updateRequest.getLeaveTypeId() != null) {
            updatedLeaveType = leaveTypeRepo.findById(updateRequest.getLeaveTypeId())
                    .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + updateRequest.getLeaveTypeId()));
        }

        LeaveRequestValidationDTO validationDTO = LeaveRequestValidationDTO.builder()
                .leaveId(request.getLeaveId())
                .employeeId(request.getEmployee().getEmployeeId())
                .leaveTypeId(updateRequest.getLeaveTypeId() != null ? updateRequest.getLeaveTypeId() : updatedLeaveType.getLeaveTypeId())
                .startDate(updateRequest.getStartDate() != null ? updateRequest.getStartDate() : request.getStartDate())
                .endDate(updateRequest.getEndDate() != null ? updateRequest.getEndDate() : request.getEndDate())
                .daysRequested(updateRequest.getDaysRequested() != null ? updateRequest.getDaysRequested() : request.getDaysRequested())
                .reason(updateRequest.getReason() != null ? updateRequest.getReason() : request.getReason())
                .driveLink(updateRequest.getDriveLink() != null ? updateRequest.getDriveLink() : request.getDriveLink())
                .requestDate(request.getRequestDate())
                .build();

        ValidationResultDTO validationResult = validateLeaveRequest(validationDTO);
        if (!validationResult.isValid()) {
            throw new RuntimeException("Validation failed: " + String.join(", ", validationResult.getErrors()));
        }

        Map<String, String> changes = new LinkedHashMap<>();
        if (updateRequest.getLeaveTypeId() != null && !updatedLeaveType.getLeaveTypeId().equals(request.getLeaveType().getLeaveTypeId())) {
            changes.put("Leave Type", request.getLeaveType().getLeaveName() + " → " + updatedLeaveType.getLeaveName());
            request.setLeaveType(updatedLeaveType);
        }
        if (updateRequest.getStartDate() != null && !updateRequest.getStartDate().equals(request.getStartDate())) {
            changes.put("Start Date", request.getStartDate() + " → " + updateRequest.getStartDate());
            request.setStartDate(updateRequest.getStartDate());
        }
        if (updateRequest.getEndDate() != null && !updateRequest.getEndDate().equals(request.getEndDate())) {
            changes.put("End Date", request.getEndDate() + " → " + updateRequest.getEndDate());
            request.setEndDate(updateRequest.getEndDate());
        }
        if (updateRequest.getDaysRequested() != null && !updateRequest.getDaysRequested().equals(request.getDaysRequested())) {
            changes.put("Days Requested", request.getDaysRequested() + " → " + updateRequest.getDaysRequested());
            request.setDaysRequested(updateRequest.getDaysRequested());
        }
        if (updateRequest.getReason() != null && !updateRequest.getReason().equals(request.getReason())) {
            changes.put("Reason", "Updated");
            request.setReason(updateRequest.getReason());
        }

        leaveBalanceService.updateLeaveBalanceAfterApproval(
                request.getEmployee().getEmployeeId(),
                updateRequest.getLeaveTypeId(),
                updateRequest.getDaysRequested(),
                request.getRequestDate().getYear()
        );
        request.setStartSession(updateRequest.getStartSession());
        request.setEndSession(updateRequest.getEndSession());
        
        LeaveRequest updatedRequest = leaveRequestRepo.save(request);

        if (!changes.isEmpty()) {
            String email = request.getEmployee().getEmail();
            if (email != null) {
                Map<String, Object> templateModel = new LinkedHashMap<>();
                templateModel.put("title", "Leave Request Updated");
                templateModel.put("recipientName", request.getEmployee().getFirstName());
                templateModel.put("messageBody", "Your leave request has been updated by your manager, <strong>" + request.getEmployee().getManager().getFirstName() + "</strong>.");
                templateModel.put("detailsTitle", "Updated Details");
                templateModel.put("details", changes);
                emailService.sendEmailFromTemplate(email, "Leave Request Updated", "generic-notification.html", templateModel);
            }
        }

        return updatedRequest;
    }

    @Override
    public LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        return leaveBalanceService.getLeaveBalance(employeeId, leaveTypeId, year);
    }

    @Override
    @Transactional
    public ValidationResultDTO updateRequestByEmployee(LeaveRequest leaveRequest, LeaveRequestValidationDTO request) {
        return leaveRequestRepo.findByLeaveIdAndEmployee_EmployeeId(
                        leaveRequest.getLeaveId(), leaveRequest.getEmployee().getEmployeeId())
                .map(existingRequest -> {
                    if (existingRequest.getStatus() == LeaveStatus.APPROVED ||
                            existingRequest.getStatus() == LeaveStatus.REJECTED) {
                        throw new LeaveBalanceExceptionHandler("Cannot update a leave request that has already been approved or rejected.");
                    }

                    leaveBalanceService.updateLeaveBalanceAfterRejected(
                            existingRequest.getEmployee().getEmployeeId(),
                            existingRequest.getLeaveType().getLeaveTypeId(),
                            existingRequest.getDaysRequested(),
                            existingRequest.getRequestDate().getYear()
                    );

                    LeaveType updatedLeaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();
                    if (updatedLeaveType == null) {
                        throw new LeaveBalanceExceptionHandler("Leave type not found: " + request.getLeaveTypeId());
                    }

                    ValidationResultDTO validationResult = validateLeaveRequest(request);
                    if (!validationResult.isValid()) {
                        return validationResult;
                    }

                    Map<String, String> changes = new LinkedHashMap<>();
                    if (!existingRequest.getLeaveType().getLeaveTypeId().equals(updatedLeaveType.getLeaveTypeId())) {
                        changes.put("Leave Type", existingRequest.getLeaveType().getLeaveName() + " → " + updatedLeaveType.getLeaveName());
                    }
                    if (!existingRequest.getStartDate().equals(request.getStartDate())) {
                        changes.put("Start Date", existingRequest.getStartDate() + " → " + request.getStartDate());
                    }
                    if (!existingRequest.getEndDate().equals(request.getEndDate())) {
                        changes.put("End Date", existingRequest.getEndDate() + " → " + request.getEndDate());
                    }
                    if (existingRequest.getDaysRequested() != request.getDaysRequested()) {
                        changes.put("Days Requested", existingRequest.getDaysRequested() + " → " + request.getDaysRequested());
                    }
                    if (!Objects.equals(existingRequest.getReason(), request.getReason())) {
                        changes.put("Reason", "Updated");
                    }

                    existingRequest.setLeaveType(updatedLeaveType);
                    existingRequest.setStartDate(request.getStartDate());
                    existingRequest.setEndDate(request.getEndDate());
                    existingRequest.setDaysRequested(request.getDaysRequested());
                    existingRequest.setReason(request.getReason());
                    existingRequest.setDriveLink(request.getDriveLink());
                    existingRequest.setStartSession(request.getStartSession());
                    existingRequest.setEndSession(request.getEndSession());
                    existingRequest.setApprovedBy(null);
                    existingRequest.setResponseDate(null);
                    existingRequest.setManagerComment(null);
                    existingRequest.setStatus(LeaveStatus.PENDING);

                    leaveBalanceService.updateLeaveBalanceAfterApproval(
                            request.getEmployeeId(),
                            request.getLeaveTypeId(),
                            request.getDaysRequested(),
                            existingRequest.getRequestDate().getYear()
                    );

                    LeaveRequest updatedRequest = leaveRequestRepo.save(existingRequest);

                    if (!changes.isEmpty()) {
                        String managerEmail = updatedRequest.getEmployee().getManager() != null ?
                                updatedRequest.getEmployee().getManager().getEmail() : null;
                        if (managerEmail != null && !managerEmail.isEmpty()) {
                            Map<String, Object> templateModel = new LinkedHashMap<>();
                            templateModel.put("title", "Leave Request Updated");
                            templateModel.put("recipientName", updatedRequest.getEmployee().getManager().getFirstName());
                            templateModel.put("messageBody", "A leave request from <strong>" + updatedRequest.getEmployee().getFullName() + "</strong> has been updated.");
                            templateModel.put("detailsTitle", "Updated Details");
                            templateModel.put("details", changes);
                            emailService.sendEmailFromTemplate(managerEmail, "Leave Request Updated", "generic-notification.html", templateModel);
                        }
                    }

                    validationResult.addMessage("Leave request updated successfully.");
                    validationResult.setLeaveId(updatedRequest.getLeaveId());

                    return validationResult;
                })
                .orElseThrow(() -> new LeaveBalanceExceptionHandler("Leave request not found for given ID and employee."));
    }

    public List<LeaveRequest> getPendingLeaveRequestsByEmployee(String employeeId) {
        return leaveRequestRepo.findByEmployee_EmployeeIdAndStatus(employeeId, LeaveStatus.PENDING);
    }
    
    @Override
    public List<LeaveRequestDTO> getAllLeaveRequestsExceptCancelled(String empId, Integer month, Integer year) {
        LocalDate today = LocalDate.now();
        int targetMonth = (month != null) ? month : today.getMonthValue();
        int targetYear = (year != null) ? year : today.getYear();
        
        LocalDate monthStart = LocalDate.of(targetYear, targetMonth, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        List<LeaveRequest> leaves = leaveRequestRepo.findActiveNonCancelledLeavesForMonth(empId, monthStart, monthEnd);
        
        if (leaves.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No leave requests found for the specified period");
        }

        return leaves.stream()
                .map(LeaveRequestDTO::new)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<LeaveRequestDTO> getAllEmployeesLeaveRequestsByMonthYear(Integer month, Integer year) {
        LocalDate today = LocalDate.now();
        int targetMonth = (month != null) ? month : today.getMonthValue();
        int targetYear = (year != null) ? year : today.getYear();
        
        LocalDate monthStart = LocalDate.of(targetYear, targetMonth, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        List<LeaveRequest> leaves = leaveRequestRepo.findAllActiveNonCancelledLeavesForMonth(monthStart, monthEnd);
        
        if (leaves.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No leave requests found for the specified period");
        }

        return leaves.stream()
                .map(LeaveRequestDTO::new)
                .collect(Collectors.toList());
    }


    @Override
    public List<LeaveRequest> leaveBalanceViewDetails(String employeeId, String leaveName, Integer year) {
        return leaveRequestRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveNameAndYear(employeeId, leaveName, year).stream().filter(obj -> obj.getStatus().equals(LeaveStatus.APPROVED) || obj.getStatus().equals(LeaveStatus.PENDING)).toList();
    }

}