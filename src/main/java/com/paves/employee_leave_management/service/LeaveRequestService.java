package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.*;
import jakarta.persistence.Transient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.text.html.Option;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
    private AsyncNotificationServiceInterface asyncNotificationService;

    @Autowired
    private GenderBasedLeaveServiceInterface genderBasedLeaveServiceInterface;
    @Autowired
    private HolidaysServiceInterface holidaysService;

    @Autowired
    private GenderBasedRepo genderBasedRepo;

    @Autowired
    private GenderBasedLeaveBalanceServiceInterface genderBasedLeaveBalanceServiceInterface;

    private static final List<String> GENDER_BASED_IDS = List.of("L-ML", "L-PL");

    // ==================== VALIDATION METHODS ====================

    @Override
    public ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO result = ValidationResultDTO.builder()
                .isValid(true)
                .employeeId(request.getEmployeeId())
                .requestedDays((float) request.getDaysRequested())
                .build();

        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();

        Optional<GenderBasedLeave> genderBasedLeave;
        LeaveType leaveType;
        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        if(request.getLeaveTypeId().equals("L-PL") || request.getLeaveTypeId().equals("L-ML")){
            genderBasedLeave = genderBasedRepo.findByLeaveTypeId(request.getLeaveTypeId());
            leaveTypeDTO.setGenderBasedLeave(genderBasedLeave.get());
        }else{
            leaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();
            leaveTypeDTO.setLeaveType(leaveType);
        }
        if (!validateBasicRequirements(request, result, employee, leaveTypeDTO)) {
            return result;
        }

        if (employee != null) {
            result.setEmployeeName(employee.getFullName());
        }

        validateBasicDateConstraints(request, result);
        validateLeaveBalance(request, result, employee, leaveTypeDTO);
        validateLeaveConflicts(request, result);
        validateLeaveTypeSpecificRules(request, result, employee, leaveTypeDTO);

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
                                              Employee employee, LeaveTypeDTO leaveType) {
        if (employee == null) {
            result.addError("Employee not found");
            return false;
        }

        if (leaveType.getLeaveType() == null && leaveType.getGenderBasedLeave() == null) {
            result.addError("Leave type not found");
            return false;
        }

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            result.addError("Leave reason/comments are mandatory");
        }

        validateDriveLinkRequirements(request, result, leaveType);

        return true;
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

    private void validateDriveLinkRequirements(LeaveRequestValidationDTO request, ValidationResultDTO result, LeaveTypeDTO leaveType) {

        if(leaveType.getGenderBasedLeave() != null){
            if (leaveType.getGenderBasedLeave().getRequiresDocumentation()) {
                if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                    result.addError("Drive link with supporting documents is required for " + leaveType.getGenderBasedLeave().getLeaveName());
                } else {
                    validateDriveLinkFormat(request.getDriveLink(), result);
                }
            }
        }else{
            if(leaveType.getLeaveType().getLeaveTypeId().equals("L-SL") && request.getDaysRequested() > 3){
                if (request.getDriveLink() == null || request.getDriveLink().trim().isEmpty()) {
                    result.addError("Drive link with medical certificate is mandatory for sick leave exceeding 3 days");
                } else {
                    validateDriveLinkFormat(request.getDriveLink(), result);
                }
            }
        }

    }

    private void validateLeaveBalance(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                      Employee employee, LeaveTypeDTO leaveType) {
        Integer currentYear = LocalDate.now().getYear();
        LeaveBalanceDTO balance = null;

        GenderBasedLeaveBalance genderBasedLeaveBalance = null;
        if(leaveType.getLeaveType() != null){
            balance = leaveBalanceService.getLeaveBalance(
                    request.getEmployeeId(), request.getLeaveTypeId(), currentYear);
            if (balance == null) {
                result.addError("Leave balance not found for the current year");
                return;
            }
        }else{
           genderBasedLeaveBalance =  genderBasedLeaveBalanceServiceInterface.getCurrentYearBalancesForEmployee(request.getEmployeeId());
           if(genderBasedLeaveBalance == null){
               result.addError("Leave balance not found for the current year");
               return;
           }
        }

        if(leaveType.getLeaveType() != null){
            if (!leaveType.getLeaveType().getLeaveTypeId().equals("L-UP") && !leaveType.getLeaveType().getAllowNegativeBalance() &&
                    balance != null && balance.getRemainingLeaves() < request.getDaysRequested()) {
                result.addError(String.format(
                        "Insufficient %s balance. Available: %.2f days, Requested: %.2f days",
                        leaveType.getLeaveType().getLeaveName(), balance.getRemainingLeaves(), request.getDaysRequested()));
            }

            if (!"L-UP".equalsIgnoreCase(leaveType.getLeaveType().getLeaveTypeId()) &&
                    leaveType.getLeaveType().getWaitingPeriodDays() != null && leaveType.getLeaveType().getWaitingPeriodDays() > 0) {
                LocalDate eligibleDate = employee.getHireDate().plusDays(leaveType.getLeaveType().getWaitingPeriodDays());
                if (LocalDate.now().isBefore(eligibleDate)) {
                    result.addError(String.format(
                            "Employee not eligible for %s. Waiting period: %d days from hire date",
                            leaveType.getLeaveType().getLeaveName(), leaveType.getLeaveType().getWaitingPeriodDays()));
                }
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
            // ✅ Use helper instead of existing.getLeaveType().getLeaveName() which NPEs for gender-based
            result.addError(String.format(
                    "Leave request overlaps with existing %s leave from %s to %s",
                    existing.getResolvedLeaveName(), // ✅ fixed
                    existing.getStartDate(),
                    existing.getEndDate()));
        }
    }



    private void validateLeaveTypeSpecificRules(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                                Employee employee, LeaveTypeDTO leaveType) {
        String leaveTypeId = request.getLeaveTypeId();
        LeaveType regular = leaveType.getLeaveType();
        GenderBasedLeave genderBasedLeave = leaveType.getGenderBasedLeave();

        switch (leaveTypeId.toUpperCase()) {
            case "L-ML":
                validateMaternityLeaveRules(request, result, employee, genderBasedLeave);
                break;
            case "L-PL":
                validatePaternityLeaveRules(request, result, employee, genderBasedLeave);
                break;
            case "L-COMPOFF":
                validateCompensatoryLeaveRules(request, result, employee, regular);
                break;
            case "L-SL":
                validateSickLeaveRules(request, result, employee, regular);
                break;
            case "L-EL":
                validateEarnedLeaveRules(request, result, employee, regular);
                break;
            case "L-UP":
                validateUnpaidLeaveRules(request, result, employee, regular);
                break;
            default:
                validateDefaultLeaveRules(request, result, employee, regular);
                break;
        }

        if(leaveType.getLeaveType() != null){
            if (!leaveType.getLeaveType().getAllowHalfDay() && request.getDaysRequested() < 1) {
                result.addError(String.format("%s does not allow half-day leave", leaveType.getLeaveType().getLeaveName()));
            }
        }

    }

    private void validateMaternityLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                             Employee employee, GenderBasedLeave leaveType) {
        // ✅ Use gender-based count query
        int pendingCount = leaveRequestRepo.countPendingGenderBasedLeavesByType(employee.getEmployeeId(), "L-ML");
        if (pendingCount > 0) {
            result.addError("You already have a pending maternity leave request.");
            return;
        }

        // ✅ Use gender-based approved query
        List<LeaveRequest> approvedML = leaveRequestRepo.findApprovedGenderBasedLeavesByType(employee.getEmployeeId(), "L-ML");
        long longLeaves = approvedML.stream()
                .filter(lr -> lr.getDaysRequested() >= 48)
                .count();

        if (request.getDaysRequested() >= 48 && longLeaves >= 2) {
            result.addError("Maternity leave for 6 months (48+ days) can only be availed twice.");
            return;
        }

        if (request.getDaysRequested() >= 48 &&
                request.getDaysRequested() != genderBasedRepo.findByLeaveTypeId("L-ML").get().getMaxLeaveDays()) {
            result.addError("Standard maternity leave should be exactly " +
                    genderBasedRepo.findByLeaveTypeId("L-ML").get().getMaxLeaveDays() + " days.");
        }

        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setGenderBasedLeave(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
    }

    private void validatePaternityLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                             Employee employee, GenderBasedLeave leaveType) {
        // ✅ Use gender-based count query
        int pendingCount = leaveRequestRepo.countPendingGenderBasedLeavesByType(employee.getEmployeeId(), "L-PL");
        if (pendingCount > 0 && request.getLeaveId() == null) {
            result.addError("You already have a pending paternity leave request. Please wait for it to be approved or rejected.");
            return;
        }

        // ✅ Use gender-based approved query
        List<LeaveRequest> approvedPL = leaveRequestRepo.findApprovedGenderBasedLeavesByType(employee.getEmployeeId(), "L-PL");

        if (approvedPL.size() >= 2) {
            result.addError("Paternity leave can only be availed twice. You have already used the maximum limit.");
            return;
        }

        if (request.getDaysRequested() != genderBasedRepo.findByLeaveTypeId("L-PL").get().getMaxLeaveDays()) {
            result.addError("Paternity leave must be exactly 5 continuous days.");
        }

        if (approvedPL.size() == 1) {
            LeaveRequest previousLeave = approvedPL.get(0);
            long gap = ChronoUnit.DAYS.between(previousLeave.getStartDate(), request.getStartDate());
            if (gap < 365) {
                result.addError("There must be a minimum 1-year gap between two paternity leaves.");
            }
        }

        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setGenderBasedLeave(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
    }

    private void validateCompensatoryLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            result.addError("Compensatory leave requires documentation/proof of overtime work");
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setLeaveType(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
    }

    private void validateSickLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && request.getDaysRequested() > 3 && request.getDriveLink() == null) {
            result.addError("Sick leave for more than 3 days requires medical certificate");
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setLeaveType(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
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
        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setLeaveType(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
    }

    private void validateUnpaidLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        if (leaveType.getRequiresDocumentation() && (request.getReason() == null || request.getReason().trim().isEmpty())) {
            result.addError("Unpaid leave requires detailed justification");
        }
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setLeaveType(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
    }

    private void validateDefaultLeaveRules(LeaveRequestValidationDTO request, ValidationResultDTO result, Employee employee, LeaveType leaveType) {
        validatePastDateRestrictions(request.getStartDate(), LocalDate.now(), leaveType, result);
        LeaveTypeDTO leaveTypeDTO = new LeaveTypeDTO();
        leaveTypeDTO.setLeaveType(leaveType);
        validateAdvanceNoticeRequirement(request.getStartDate(), LocalDate.now(), leaveTypeDTO, result);
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

    private void validateAdvanceNoticeRequirement(LocalDate startDate, LocalDate today, LeaveTypeDTO leaveTypeDTO, ValidationResultDTO result) {
        GenderBasedLeave genderBasedLeave;
        LeaveType regularLeave;
        if(leaveTypeDTO.getLeaveType() != null){
            LeaveType leaveType = leaveTypeDTO.getLeaveType();
            if (leaveType.getAdvanceNoticeDays() != null && leaveType.getAdvanceNoticeDays() > 0) {
                long daysBetween = ChronoUnit.DAYS.between(today, startDate);
                if (daysBetween < leaveType.getAdvanceNoticeDays()) {
                    result.addError(String.format("Leave request requires %d days advance notice",
                            leaveType.getAdvanceNoticeDays()));
                }
            }
        }else{
            GenderBasedLeave leaveType = leaveTypeDTO.getGenderBasedLeave();
            if (leaveType.getAdvanceNotice() != null && leaveType.getAdvanceNotice() > 0) {
                long daysBetween = ChronoUnit.DAYS.between(today, startDate);
                if (daysBetween < leaveType.getAdvanceNotice()) {
                    result.addError(String.format("Leave request requires %d days advance notice",
                            leaveType.getAdvanceNotice()));
                }
            }
        }

    }

    // ==================== APPLICATION METHODS ====================

    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", key = "#request.getEmployeeId() + '-' + #request.getYear()"),
                    @CacheEvict(value = "employeeLeaveBalance", key = "#request.getEmployeeId() + '-' + #request.getYear()")
            }
    )
    public LeaveRequest saveLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO validationResult = validateLeaveRequest(request);

        if (!validationResult.isValid()) {
            throw new RuntimeException("Leave request validation failed: " + String.join(", ", validationResult.getErrors()));
        }

        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();
//        GenderBasedLeave genderBasedLeave = genderBasedRepo.findByLeaveTypeId(request.getLeaveTypeId()).get();

        LeaveRequest.LeaveRequestBuilder builder = LeaveRequest.builder()
                .employee(employee)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .daysRequested(request.getDaysRequested())
                .reason(request.getReason())
                .driveLink(request.getDriveLink())
                .status(LeaveStatus.PENDING)
                .startSession(request.getStartSession())
                .endSession(request.getEndSession())
                .requestDate(LocalDate.now())
                .createdAt(LocalDateTime.now());

        String leaveName; // for email

        // ✅ Route to correct relationship
        if (request.getLeaveTypeId().equals("L-ML") || request.getLeaveTypeId().equals("L-PL")) {
            GenderBasedLeave genderBasedLeave = genderBasedLeaveServiceInterface
                    .getLeaveType(request.getLeaveTypeId())
                    .orElseThrow(() -> new RuntimeException("Gender leave type not found: " + request.getLeaveTypeId()));
            builder.genderBasedLeaveType(genderBasedLeave);
            leaveName = genderBasedLeave.getLeaveName();
        } else {
            LeaveType leaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();
            builder.leaveType(leaveType);
            leaveName = leaveType.getLeaveName();
        }
        builder.leaveName(leaveName);
        LeaveRequest leaveRequest = builder.build();
        LeaveRequest savedRequest = leaveRequestRepo.save(leaveRequest);

        if (savedRequest != null) {
            // ✅ Route balance update correctly
            if (request.getLeaveTypeId().equals("L-ML") || request.getLeaveTypeId().equals("L-PL")) {
                genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterApproval(
                        request.getEmployeeId(),
                        request.getLeaveTypeId(),
                        request.getDaysRequested(),
                        leaveRequest.getRequestDate().getYear());
            } else {
                leaveBalanceService.updateLeaveBalanceAfterApproval(
                        request.getEmployeeId(),
                        request.getLeaveTypeId(),
                        request.getDaysRequested(),
                        leaveRequest.getRequestDate().getYear());
            }

            // ✅ Email notification — use leaveName resolved above
            if (employee.getManager() != null && employee.getManager().getEmail() != null) {
                Map<String, Object> templateModel = new LinkedHashMap<>();
                templateModel.put("title", "New Leave Application");
                templateModel.put("recipientName", employee.getManager().getFirstName());
                templateModel.put("messageBody", "A new leave application has been submitted by <strong>" + employee.getFullName() + "</strong>.");
                templateModel.put("detailsTitle", "Application Details");

                Map<String, String> details = new LinkedHashMap<>();
                details.put("Employee", employee.getFullName());
                details.put("Leave Type", leaveName); // ✅ resolved correctly
                details.put("Start Date", request.getStartDate().toString());
                details.put("End Date", request.getEndDate().toString());
                details.put("Reason", request.getReason());
                templateModel.put("details", details);
                templateModel.put("closingMessage", "Please review the application in the Leave Management System.");

                EmailDTO emailDTO = new EmailDTO(
                        employee.getManager().getEmail(),
                        "New Leave Application - " + employee.getFullName(),
                        "generic-notification.html", true);
                emailDTO.setTemplateModel(templateModel);
                asyncNotificationService.queueEmail(emailDTO);
            }
        }

        return savedRequest;
    }

    @Override
    @Cacheable(value = "leaveRequestsByEmployee", key = "#employeeId")
    public List<LeaveRequestResponseDTO> getLeaveRequestsByEmployee(String employeeId) {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findByEmployee_EmployeeId(employeeId);
        return leaveRequests.stream().filter(leaveRequest ->
                leaveRequest.getStatus() != LeaveStatus.PENDING)
                .map(leave -> LeaveRequestResponseDTO.builder()
                        .employeeId(leave.getEmployee().getEmployeeId())
                        .employeeFullName(leave.getEmployee().getFullName())
                        .daysRequested(leave.getDaysRequested())
                        .startDate(leave.getStartDate())
                        .endDate(leave.getEndDate())
                        .startSession(leave.getStartSession())
                        .endSession(leave.getEndSession())
                        .leaveId(leave.getLeaveId())
                        .leaveTypeId(getResolvedLeaveTypeId(leave))
                        .reason(leave.getReason())
                        .driveLink(leave.getDriveLink())
                        .status(leave.getStatus())
                        .leaveName(resolveLeaveLabel(leave.getResolvedLeaveName()))
                        .managerComment(leave.getManagerComment())
                        .year(leave.getYear())
                        .requestDate(leave.getRequestDate())
                        .approvedBy(leave.getEmployee().getManager().getEmployeeId())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "leaveRequestsByEmployeeAndYear", key = "#employeeId + '-' + #year")
    public List<LeaveRequestResponseDTO> getLeaveRequestsByEmployeeAndByYear(String employeeId, int year) {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);
        return
                leaveRequests.stream()
                        .filter(leaveRequest ->
                                leaveRequest.getStatus() != LeaveStatus.PENDING )
                        .map(leave -> LeaveRequestResponseDTO.builder()
                                .employeeId(leave.getEmployeeId())
                                .employeeFullName(leave.getEmployee().getFullName())
                                .daysRequested(leave.getDaysRequested())
                                .startDate(leave.getStartDate())
                                .endDate(leave.getEndDate())
                                .startSession(leave.getStartSession())
                                .endSession(leave.getEndSession())
                                .leaveId(leave.getLeaveId())
                                .leaveTypeId(getResolvedLeaveTypeId(leave))
                                .reason(leave.getReason())
                                .driveLink(leave.getDriveLink())
                                .approvedBy(getApprovedBy(leave))
                                .managerComment(leave.getManagerComment())
                                .status(leave.getStatus())
                                .leaveName(resolveLeaveLabel(leave.getResolvedLeaveName()))
                                .year(leave.getYear())
                                .build())
                        .collect(Collectors.toList());
    }

    @Override
    public LeaveRequest getLeaveRequestById(String leaveId) {
        return leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + leaveId));
    }

    private String getApprovedBy(LeaveRequest request){
        if(request.getApprovedBy() == null){
            return "unknown";
        }
        return request.getApprovedBy().getFullName();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(
                    value = "pendingLeaveRequestsByEmployeeAndYear",
                    key = "#employeeId + '-' + #result.requestDate.year"
            ),
            @CacheEvict(
                    value = "leaveRequestsByEmployeeAndYear",
                    key = "#employeeId + '-' + #result.requestDate.year"
            ),
            @CacheEvict(
                    value = "employeeLeaveBalance",
                    key = "#employeeId + '-' + #result.requestDate.year"
            )
    })
    public LeaveRequest cancelLeaveRequest(String leaveId, String employeeId) {

        // Fetch leave request
        LeaveRequest request = leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        // Authorization check
        if (!request.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Unauthorized: You can only cancel your own leave requests");
        }

        // Status validation
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new RuntimeException("Cannot cancel a leave request that is not pending");
        }

        // Update request status
        request.setStatus(LeaveStatus.CANCELLED);
        request.setResponseDate(LocalDate.now());

        int year = request.getRequestDate().getYear();

        // Update leave balance
        if (request.getGenderBasedLeaveType() != null) {
            genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterRejected(
                    employeeId,
                    request.getGenderBasedLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    year
            );
        } else {
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    employeeId,
                    request.getLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    year
            );
        }

        // Save updated request
        LeaveRequest cancelledRequest = leaveRequestRepo.save(request);

        // Notify manager
        Employee employee = cancelledRequest.getEmployee();
        Employee manager = employee.getManager();

        if (manager != null && manager.getEmail() != null) {

            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Leave Request Cancelled");
            templateModel.put("recipientName", manager.getFirstName());
            templateModel.put(
                    "messageBody",
                    "A leave request from <strong>" + employee.getFullName() + "</strong> has been cancelled by the employee."
            );
            templateModel.put("detailsTitle", "Cancelled Request Details");

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Employee", employee.getFullName());

            String leaveTypeLabel = request.getGenderBasedLeaveType() != null
                    ? resolveLeaveLabel(request.getGenderBasedLeaveType().getLeaveTypeId())
                    : resolveLeaveLabel(request.getLeaveType().getLeaveTypeId());

            details.put("Leave Type", leaveTypeLabel);
            details.put("Start Date", cancelledRequest.getStartDate().toString());
            details.put("End Date", cancelledRequest.getEndDate().toString());

            templateModel.put("details", details);

            EmailDTO emailDTO = new EmailDTO(
                    manager.getEmail(),
                    "Leave Request Cancelled - " + employee.getFullName(),
                    "generic-notification.html",
                    true
            );

            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
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
//    @Cacheable(value = "leaveHistory", key = "#queryDTO.managerId + '-' + #queryDTO.year")
    public List<LeaveRequestManagerViewDTO> getRequestsForManager(ManagerQueryDTO queryDTO) {
        List<LeaveRequest> leaveRequest =  leaveRequestRepo.findManagerRequestsByCriteria(queryDTO);
        return leaveRequest.stream().map((leave)->
                LeaveRequestManagerViewDTO.builder()
                        .leaveId(leave.getLeaveId())
                        .requestDate(leave.getRequestDate())
                        .employeeId(leave.getEmployee().getEmployeeId())
                        .employeeFullName(leave.getEmployee().getFullName())
                        .startDate(leave.getStartDate())
                        .endDate(leave.getEndDate())
//                        .approvedBy(leave.getEmployee().getManager().getEmployeeId())
                        .year(leave.getYear())
                        .leaveTypeId(getResolvedLeaveTypeId(leave))
                        .leaveName(resolveLeaveLabel(leave.getResolvedLeaveName()))
                        .driveLink(leave.getDriveLink())
                        .startSession(leave.getStartSession())
                        .endSession(leave.getEndSession())
                        .status(leave.getStatus())
                        .daysRequested(leave.getDaysRequested())
                        .reason(leave.getReason())
                        .jobTitle(leave.getEmployee().getJobTitle())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<LeaveRequest> getLeaveHistoryForManager(ManagerQueryDTO queryDTO) {
        return leaveRequestRepo.findManagerHistoryByCriteria(queryDTO);
    }

    @Override
    @Transactional
    @Caching(evict = {

            // Employee caches
            @CacheEvict(
                    value = "pendingLeaveRequestsByEmployeeAndYear",
                    key = "#result.employee.employeeId + '-' + #approvalRequest.year"
            ),
            @CacheEvict(
                    value = "leaveRequestsByEmployeeAndYear",
                    key = "#result.employee.employeeId + '-' + #approvalRequest.year"
            )
    })
    public LeaveRequest approveRequest(ApprovalRequestDTO approvalRequest) {

        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(
                        approvalRequest.getLeaveId(),
                        approvalRequest.getManagerId()
                )
                .orElseThrow(() -> new RuntimeException(
                        "Leave request not found with ID: "
                                + approvalRequest.getLeaveId() + " for this manager"
                ));

        Employee manager = employeeRepo.findById(approvalRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException(
                        "Manager not found with ID: " + approvalRequest.getManagerId()
                ));

        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());

        if (approvalRequest.getComment() != null && !approvalRequest.getComment().trim().isEmpty()) {
            request.setManagerComment(approvalRequest.getComment());
        }

        LeaveRequest approvedRequest = leaveRequestRepo.save(request);

        // Email logic (unchanged)
        if (approvedRequest.getEmployee().getEmail() != null) {

            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Leave Application Approved");
            templateModel.put("recipientName", approvedRequest.getEmployee().getFullName());
            templateModel.put("messageBody",
                    "Your leave application for <strong>"
                            + resolveLeaveLabel(approvedRequest.getResolvedLeaveName())
                            + "</strong> has been approved.");
            templateModel.put("detailsTitle", "Approval Details");

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Leave Type", resolveLeaveLabel(approvedRequest.getResolvedLeaveTypeId()));
            details.put("Start Date", approvedRequest.getStartDate().toString());
            details.put("End Date", approvedRequest.getEndDate().toString());

            if (approvalRequest.getComment() != null) {
                details.put("Manager's Comment", approvalRequest.getComment());
            }

            templateModel.put("details", details);

            EmailDTO emailDTO = new EmailDTO(
                    approvedRequest.getEmployee().getEmail(),
                    "Leave Application Approved",
                    "generic-notification.html",
                    true
            );

            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
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

            if(request.getLeaveType() != null){
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear());
            }else{
                genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterRejected(
                        request.getEmployee().getEmployeeId(),
                        request.getGenderBasedLeaveType().getLeaveTypeId(),
                        request.getDaysRequested(),
                        request.getRequestDate().getYear()
                );
            }
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
        if(request.getLeaveType() != null){
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear());
        }else{
            genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getGenderBasedLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear()
            );
        }



        if (request.getEmployee().getEmail() != null) {
            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Leave Application Rejected");
            templateModel.put("recipientName", request.getEmployee().getFullName());
            templateModel.put("messageBody", "Your leave application for <strong>" + resolveLeaveLabel(request.getResolvedLeaveName()) + "</strong> has been rejected.");
            templateModel.put("detailsTitle", "Rejection Details");

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Leave Type", resolveLeaveLabel(request.getResolvedLeaveName()));
            details.put("Start Date", request.getStartDate().toString());
            details.put("End Date", request.getEndDate().toString());
            details.put("Rejection Reason", rejectionRequest.getComment());
            templateModel.put("details", details);

            EmailDTO emailDTO = new EmailDTO(request.getEmployee().getEmail(), "Leave Application Rejected", "generic-notification.html", true);
            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
        }

        return rejectedRequest;
    }

    @Override
    @Transactional
    public LeaveRequest updateLeaveRequestByManager(ManagerUpdateRequestDTO updateRequest) {
        LeaveRequest request = leaveRequestRepo
                .findByLeaveIdAndEmployee_Manager_EmployeeId(updateRequest.getLeaveId(), updateRequest.getManagerId())
                .orElseThrow(() -> new RuntimeException("Leave request not found with ID: " + updateRequest.getLeaveId() + " for this manager"));

        // ✅ Resolve existing leave type using helpers
        String existingLeaveTypeId = request.getResolvedLeaveTypeId();
        boolean existingIsGenderBased = GENDER_BASED_IDS.contains(existingLeaveTypeId);

        // ✅ Step 1: Rollback existing balance — route to correct service
        if (existingIsGenderBased) {
            genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    existingLeaveTypeId,
                    request.getDaysRequested(),
                    request.getRequestDate().getYear()
            );
        } else {
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    existingLeaveTypeId,
                    request.getDaysRequested(),
                    request.getRequestDate().getYear()
            );
        }

        // ✅ Step 2: Resolve the new leave type ID (use existing if not provided)
        String newLeaveTypeId = updateRequest.getLeaveTypeId() != null
                ? updateRequest.getLeaveTypeId()
                : existingLeaveTypeId;

        boolean newIsGenderBased = GENDER_BASED_IDS.contains(newLeaveTypeId);

        // ✅ Step 3: Build validation DTO — leaveTypeId is always resolved
        LeaveRequestValidationDTO validationDTO = LeaveRequestValidationDTO.builder()
                .leaveId(request.getLeaveId())
                .employeeId(request.getEmployee().getEmployeeId())
                .leaveTypeId(newLeaveTypeId)
                .startDate(updateRequest.getStartDate() != null ? updateRequest.getStartDate() : request.getStartDate())
                .endDate(updateRequest.getEndDate() != null ? updateRequest.getEndDate() : request.getEndDate())
                .daysRequested(updateRequest.getDaysRequested() != null ? updateRequest.getDaysRequested() : request.getDaysRequested())
                .reason(updateRequest.getReason() != null ? updateRequest.getReason() : request.getReason())
                .driveLink(updateRequest.getDriveLink() != null ? updateRequest.getDriveLink() : request.getDriveLink())
                .requestDate(request.getRequestDate())
                .build();

        ValidationResultDTO validationResult = validateLeaveRequest(validationDTO);
        if (!validationResult.isValid()) {
            // ✅ Re-apply the rolled-back balance since we're aborting
            if (existingIsGenderBased) {
                genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterApproval(
                        request.getEmployee().getEmployeeId(),
                        existingLeaveTypeId,
                        request.getDaysRequested(),
                        request.getRequestDate().getYear()
                );
            } else {
                leaveBalanceService.updateLeaveBalanceAfterApproval(
                        request.getEmployee().getEmployeeId(),
                        existingLeaveTypeId,
                        request.getDaysRequested(),
                        request.getRequestDate().getYear()
                );
            }
            throw new RuntimeException("Validation failed: " + String.join(", ", validationResult.getErrors()));
        }

        // ✅ Step 4: Track changes using resolved names
        Map<String, String> changes = new LinkedHashMap<>();

        if (updateRequest.getLeaveTypeId() != null && !newLeaveTypeId.equals(existingLeaveTypeId)) {
            changes.put("Leave Type", request.getResolvedLeaveName() + " → " + resolveLeaveLabelUpdateManager(newLeaveTypeId));
        }
        if (updateRequest.getStartDate() != null && !updateRequest.getStartDate().equals(request.getStartDate())) {
            changes.put("Start Date", request.getStartDate() + " → " + updateRequest.getStartDate());
        }
        if (updateRequest.getEndDate() != null && !updateRequest.getEndDate().equals(request.getEndDate())) {
            changes.put("End Date", request.getEndDate() + " → " + updateRequest.getEndDate());
        }
        if (updateRequest.getDaysRequested() != null && updateRequest.getDaysRequested() != request.getDaysRequested()) {
            changes.put("Days Requested", request.getDaysRequested() + " → " + updateRequest.getDaysRequested());
        }
        if (updateRequest.getReason() != null && !updateRequest.getReason().equals(request.getReason())) {
            changes.put("Reason", "Updated");
        }

        // ✅ Step 5: Update entity — route to correct relationship
        if (newIsGenderBased) {
            GenderBasedLeave genderLeave = genderBasedLeaveServiceInterface
                    .getLeaveType(newLeaveTypeId)
                    .orElseThrow(() -> new RuntimeException("Gender leave type not found: " + newLeaveTypeId));
            request.setGenderBasedLeaveType(genderLeave);
            request.setLeaveType(null); // ✅ clear the other side
        } else {
            LeaveType leaveType = leaveTypeRepo.findById(newLeaveTypeId)
                    .orElseThrow(() -> new RuntimeException("Leave type not found: " + newLeaveTypeId));
            request.setLeaveType(leaveType);
            request.setGenderBasedLeaveType(null); // ✅ clear the other side
        }

        // ✅ Step 6: Apply common field updates
        if (updateRequest.getStartDate() != null) request.setStartDate(updateRequest.getStartDate());
        if (updateRequest.getEndDate() != null) request.setEndDate(updateRequest.getEndDate());
        if (updateRequest.getDaysRequested() != null) request.setDaysRequested(updateRequest.getDaysRequested());
        if (updateRequest.getReason() != null) request.setReason(updateRequest.getReason());
        if (updateRequest.getDriveLink() != null) request.setDriveLink(updateRequest.getDriveLink());
        request.setStartSession(updateRequest.getStartSession());
        request.setEndSession(updateRequest.getEndSession());

        // ✅ Step 7: Apply new balance deduction — route to correct service
        double newDays = updateRequest.getDaysRequested() != null
                ? updateRequest.getDaysRequested()
                : request.getDaysRequested();

        if (newIsGenderBased) {
            genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterApproval(
                    request.getEmployee().getEmployeeId(),
                    newLeaveTypeId,
                    newDays,
                    request.getRequestDate().getYear()
            );
        } else {
            leaveBalanceService.updateLeaveBalanceAfterApproval(
                    request.getEmployee().getEmployeeId(),
                    newLeaveTypeId,
                    newDays,
                    request.getRequestDate().getYear()
            );
        }

        LeaveRequest updatedRequest = leaveRequestRepo.save(request);

        // ✅ Step 8: Email employee about the update
        if (!changes.isEmpty()) {
            String email = request.getEmployee().getEmail();
            if (email != null) {
                Map<String, Object> templateModel = new LinkedHashMap<>();
                templateModel.put("title", "Leave Request Updated");
                templateModel.put("recipientName", request.getEmployee().getFirstName());
                templateModel.put("messageBody", "Your leave request has been updated by your manager, <strong>" +
                        request.getEmployee().getManager().getFirstName() + "</strong>.");
                templateModel.put("detailsTitle", "Updated Details");
                templateModel.put("details", changes);
                EmailDTO emailDTO = new EmailDTO(email, "Leave Request Updated", "generic-notification.html", true);
                emailDTO.setTemplateModel(templateModel);
                asyncNotificationService.queueEmail(emailDTO);
            }
        }

        return updatedRequest;
    }

    private String resolveLeaveLabelUpdateManager(String leaveTypeId) {
        if (GENDER_BASED_IDS.contains(leaveTypeId)) {
            return genderBasedLeaveServiceInterface.getLeaveType(leaveTypeId)
                    .map(GenderBasedLeave::getLeaveName)
                    .map(name -> {
                        try { return LeaveTypesEnum.valueOf(name).getLabel(); }
                        catch (IllegalArgumentException e) { return name; }
                    })
                    .orElse(leaveTypeId);
        }
        return leaveTypeRepo.findById(leaveTypeId)
                .map(lt -> {
                    try { return LeaveTypesEnum.valueOf(lt.getLeaveName()).getLabel(); }
                    catch (IllegalArgumentException e) { return lt.getLeaveName(); }
                })
                .orElse(leaveTypeId);
    }

    @Override
    public LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        return leaveBalanceService.getLeaveBalance(employeeId, leaveTypeId, year);
    }

    // old code for the update leave request
//
//    @Override
//    @Transactional
//    public ValidationResultDTO updateRequestByEmployee(LeaveRequest leaveRequest, LeaveRequestValidationDTO request) {
//        return leaveRequestRepo.findByLeaveIdAndEmployee_EmployeeId(
//                        leaveRequest.getLeaveId(), leaveRequest.getEmployee().getEmployeeId())
//                .map(existingRequest -> {
//                    if (existingRequest.getStatus() == LeaveStatus.APPROVED ||
//                            existingRequest.getStatus() == LeaveStatus.REJECTED) {
//                        throw new LeaveBalanceExceptionHandler("Cannot update a leave request that has already been approved or rejected.");
//                    }
//
//                    leaveBalanceService.updateLeaveBalanceAfterRejected(
//                            existingRequest.getEmployee().getEmployeeId(),
//                            existingRequest.getLeaveType().getLeaveTypeId(),
//                            existingRequest.getDaysRequested(),
//                            existingRequest.getRequestDate().getYear()
//                    );
//
//                    LeaveType updatedLeaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();
//                    if (updatedLeaveType == null) {
//                        throw new LeaveBalanceExceptionHandler("Leave type not found: " + request.getLeaveTypeId());
//                    }
//
//                    ValidationResultDTO validationResult = validateLeaveRequest(request);
//                    if (!validationResult.isValid()) {
//                        return validationResult;
//                    }
//
//                    Map<String, String> changes = new LinkedHashMap<>();
//                    if (!existingRequest.getLeaveType().getLeaveTypeId().equals(updatedLeaveType.getLeaveTypeId())) {
//                        changes.put("Leave Type", existingRequest.getLeaveType().getLeaveName() + " → " + updatedLeaveType.getLeaveName());
//                    }
//                    if (!existingRequest.getStartDate().equals(request.getStartDate())) {
//                        changes.put("Start Date", existingRequest.getStartDate() + " → " + request.getStartDate());
//                    }
//                    if (!existingRequest.getEndDate().equals(request.getEndDate())) {
//                        changes.put("End Date", existingRequest.getEndDate() + " → " + request.getEndDate());
//                    }
//                    if (existingRequest.getDaysRequested() != request.getDaysRequested()) {
//                        changes.put("Days Requested", existingRequest.getDaysRequested() + " → " + request.getDaysRequested());
//                    }
//                    if (!Objects.equals(existingRequest.getReason(), request.getReason())) {
//                        changes.put("Reason", "Updated");
//                    }
//
//                    existingRequest.setLeaveType(updatedLeaveType);
//                    existingRequest.setStartDate(request.getStartDate());
//                    existingRequest.setEndDate(request.getEndDate());
//                    existingRequest.setDaysRequested(request.getDaysRequested());
//                    existingRequest.setReason(request.getReason());
//                    existingRequest.setDriveLink(request.getDriveLink());
//                    existingRequest.setStartSession(request.getStartSession());
//                    existingRequest.setEndSession(request.getEndSession());
//                    existingRequest.setApprovedBy(null);
//                    existingRequest.setResponseDate(null);
//                    existingRequest.setManagerComment(null);
//                    existingRequest.setStatus(LeaveStatus.PENDING);
//
//                    leaveBalanceService.updateLeaveBalanceAfterApproval(
//                            request.getEmployeeId(),
//                            request.getLeaveTypeId(),
//                            request.getDaysRequested(),
//                            existingRequest.getRequestDate().getYear()
//                    );
//
//                    LeaveRequest updatedRequest = leaveRequestRepo.save(existingRequest);
//
//                    if (!changes.isEmpty()) {
//                        String managerEmail = updatedRequest.getEmployee().getManager() != null ?
//                                updatedRequest.getEmployee().getManager().getEmail() : null;
//                        if (managerEmail != null && !managerEmail.isEmpty()) {
//                            Map<String, Object> templateModel = new LinkedHashMap<>();
//                            templateModel.put("title", "Leave Request Updated");
//                            templateModel.put("recipientName", updatedRequest.getEmployee().getManager().getFirstName());
//                            templateModel.put("messageBody", "A leave request from <strong>" + updatedRequest.getEmployee().getFullName() + "</strong> has been updated.");
//                            templateModel.put("detailsTitle", "Updated Details");
//                            templateModel.put("details", changes);
//                            EmailDTO emailDTO = new EmailDTO(managerEmail, "Leave Request Updated", "generic-notification.html", true);
//                            emailDTO.setTemplateModel(templateModel);
//                            asyncNotificationService.queueEmail(emailDTO);
//                        }
//                    }
//
//                    validationResult.addMessage("Leave request updated successfully.");
//                    validationResult.setLeaveId(updatedRequest.getLeaveId());
//
//                    return validationResult;
//                })
//                .orElseThrow(() -> new LeaveBalanceExceptionHandler("Leave request not found for given ID and employee."));
//    }

    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", key = "#request.employeeId + '-' + #request.year")
            }
    )
    public ValidationResultDTO updateRequestByEmployee(LeaveRequest leaveRequest, LeaveRequestValidationDTO request) {
        return leaveRequestRepo.findByLeaveIdAndEmployee_EmployeeId(
                        leaveRequest.getLeaveId(), leaveRequest.getEmployee().getEmployeeId())
                .map(existingRequest -> {
                    if (existingRequest.getStatus() == LeaveStatus.APPROVED ||
                            existingRequest.getStatus() == LeaveStatus.REJECTED) {
                        throw new LeaveBalanceExceptionHandler("Cannot update a leave request that has already been approved or rejected.");
                    }

                    // ✅ Resolve existing leave type ID from whichever is set
                    String existingLeaveTypeId = existingRequest.getResolvedLeaveTypeId();
                    String existingLeaveName = existingRequest.getResolvedLeaveName();
                    boolean existingIsGenderBased = GENDER_BASED_IDS.contains(existingLeaveTypeId);

                    // ✅ Rollback the old balance — route to correct service
                    if (existingIsGenderBased) {
                        genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterRejected(
                                existingRequest.getEmployee().getEmployeeId(),
                                existingLeaveTypeId,
                                existingRequest.getDaysRequested(),
                                existingRequest.getRequestDate().getYear()
                        );
                    } else {
                        leaveBalanceService.updateLeaveBalanceAfterRejected(
                                existingRequest.getEmployee().getEmployeeId(),
                                existingLeaveTypeId,
                                existingRequest.getDaysRequested(),
                                existingRequest.getRequestDate().getYear()
                        );
                    }

                    // ✅ Resolve new leave type — route to correct entity
                    boolean newIsGenderBased = GENDER_BASED_IDS.contains(request.getLeaveTypeId());
                    String updatedLeaveName;

                    if (newIsGenderBased) {
                        GenderBasedLeave updatedGenderLeave = genderBasedLeaveServiceInterface
                                .getLeaveType(request.getLeaveTypeId())
                                .orElseThrow(() -> new LeaveBalanceExceptionHandler("Gender leave type not found: " + request.getLeaveTypeId()));

                        // ✅ Validate first
                        ValidationResultDTO validationResult = validateLeaveRequest(request);
                        if (!validationResult.isValid()) return validationResult;

                        // ✅ Track changes
                        Map<String, String> changes = buildChanges(
                                existingRequest, existingLeaveTypeId, existingLeaveName,
                                request, updatedGenderLeave.getLeaveTypeId(), updatedGenderLeave.getLeaveName()
                        );

                        // ✅ Update fields — clear regular, set gender-based
                        existingRequest.setLeaveType(null);
                        existingRequest.setGenderBasedLeaveType(updatedGenderLeave);
                        updatedLeaveName = updatedGenderLeave.getLeaveName();

                        applyCommonUpdates(existingRequest, request);

                        // ✅ Apply new balance deduction
                        genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterApproval(
                                request.getEmployeeId(),
                                request.getLeaveTypeId(),
                                request.getDaysRequested(),
                                existingRequest.getRequestDate().getYear()
                        );

                        LeaveRequest updatedRequest = leaveRequestRepo.save(existingRequest);
                        sendUpdateEmail(updatedRequest, changes);

                        validationResult.addMessage("Leave request updated successfully.");
                        validationResult.setLeaveId(updatedRequest.getLeaveId());
                        return validationResult;

                    } else {
                        LeaveType updatedLeaveType = leaveTypeService.getLeaveTypeById(request.getLeaveTypeId()).getBody();
                        if (updatedLeaveType == null) {
                            throw new LeaveBalanceExceptionHandler("Leave type not found: " + request.getLeaveTypeId());
                        }

                        // ✅ Validate first
                        ValidationResultDTO validationResult = validateLeaveRequest(request);
                        if (!validationResult.isValid()) return validationResult;

                        // ✅ Track changes
                        Map<String, String> changes = buildChanges(
                                existingRequest, existingLeaveTypeId, existingLeaveName,
                                request, updatedLeaveType.getLeaveTypeId(), updatedLeaveType.getLeaveName()
                        );

                        // ✅ Update fields — clear gender-based, set regular
                        existingRequest.setGenderBasedLeaveType(null);
                        existingRequest.setLeaveType(updatedLeaveType);
                        updatedLeaveName = updatedLeaveType.getLeaveName();

                        applyCommonUpdates(existingRequest, request);

                        // ✅ Apply new balance deduction
                        leaveBalanceService.updateLeaveBalanceAfterApproval(
                                request.getEmployeeId(),
                                request.getLeaveTypeId(),
                                request.getDaysRequested(),
                                existingRequest.getRequestDate().getYear()
                        );

                        LeaveRequest updatedRequest = leaveRequestRepo.save(existingRequest);
                        sendUpdateEmail(updatedRequest, changes);

                        validationResult.addMessage("Leave request updated successfully.");
                        validationResult.setLeaveId(updatedRequest.getLeaveId());
                        return validationResult;
                    }
                })
                .orElseThrow(() -> new LeaveBalanceExceptionHandler("Leave request not found for given ID and employee."));
    }

    // ✅ Extracted helper — apply common field updates
    private void applyCommonUpdates(LeaveRequest existingRequest, LeaveRequestValidationDTO request) {
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
    }

    private Map<String, String> buildChanges(
            LeaveRequest existingRequest,
            String existingLeaveTypeId, String existingLeaveName,
            LeaveRequestValidationDTO request,
            String newLeaveTypeId, String newLeaveName) {

        Map<String, String> changes = new LinkedHashMap<>();

        if (!existingLeaveTypeId.equals(newLeaveTypeId)) {
            changes.put("Leave Type", existingLeaveName + " → " + newLeaveName);
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
        return changes;
    }

    private void sendUpdateEmail(LeaveRequest updatedRequest, Map<String, String> changes) {
        if (changes.isEmpty()) return;

        String managerEmail = updatedRequest.getEmployee().getManager() != null
                ? updatedRequest.getEmployee().getManager().getEmail() : null;

        if (managerEmail == null || managerEmail.isEmpty()) return;

        Map<String, Object> templateModel = new LinkedHashMap<>();
        templateModel.put("title", "Leave Request Updated");
        templateModel.put("recipientName", updatedRequest.getEmployee().getManager().getFirstName());
        templateModel.put("messageBody", "A leave request from <strong>" + updatedRequest.getEmployee().getFullName() + "</strong> has been updated.");
        templateModel.put("detailsTitle", "Updated Details");
        templateModel.put("details", changes);

        EmailDTO emailDTO = new EmailDTO(managerEmail, "Leave Request Updated", "generic-notification.html", true);
        emailDTO.setTemplateModel(templateModel);
        asyncNotificationService.queueEmail(emailDTO);
    }

    public List<LeaveRequestResponseDTO> getPendingLeaveRequestsByEmployee(String employeeId) {
        List<LeaveRequest> leaveRequest = leaveRequestRepo.findByEmployee_EmployeeIdAndStatus(employeeId, LeaveStatus.PENDING);
        return leaveRequest.stream()
                .map(leave -> LeaveRequestResponseDTO.builder()
                        .employeeId(leave.getEmployeeId())
                        .employeeFullName(leave.getEmployee().getFullName())
                        .daysRequested(leave.getDaysRequested())
                        .startDate(leave.getStartDate())
                        .endDate(leave.getEndDate())
                        .startSession(leave.getStartSession())
                        .endSession(leave.getEndSession())
                        .leaveId(leave.getLeaveId())
                        .leaveTypeId(leave.getLeaveType().getLeaveTypeId())
                        .reason(leave.getReason())
                        .driveLink(leave.getDriveLink())
                        .status(leave.getStatus())
                        .leaveName(resolveLeaveLabel(leave.getResolvedLeaveName()))
                        .year(leave.getYear())
                        .build())
                .collect(Collectors.toList());
    }


    public String resolveLeaveLabel(String rawName) {
        if (rawName == null) return null;
        try {
            return LeaveTypesEnum.valueOf(rawName).getLabel(); // e.g. "PATERNITY_LEAVE" → "Paternity Leave"
        } catch (IllegalArgumentException e) {
            return rawName; // fallback to raw name if not found in enum
        }
    }

    @Override
    @Cacheable(value = "pendingLeaveRequestsByEmployeeAndYear", key = "#employeeId + '-' + #year")
    public List<LeaveRequestResponseDTO> getPendingLeaveRequestsByEmployeeAndYear(String employeeId, int year) {
        List<LeaveRequest> leaveRequest = leaveRequestRepo.findByEmployee_EmployeeIdAndStatusAndYear(employeeId, LeaveStatus.PENDING, year);
        return leaveRequest.stream()
                .map(leave -> LeaveRequestResponseDTO.builder()
                        .employeeId(leave.getEmployee().getEmployeeId())
                        .employeeFullName(leave.getEmployee().getFullName())
                        .daysRequested(leave.getDaysRequested())
                        .startDate(leave.getStartDate())
                        .endDate(leave.getEndDate())
                        .startSession(leave.getStartSession())
                        .endSession(leave.getEndSession())
                        .leaveId(leave.getLeaveId())
                        .leaveTypeId(getResolvedLeaveTypeId(leave))
                        .reason(leave.getReason())
                        .driveLink(leave.getDriveLink())
                        .status(leave.getStatus())
                        .leaveName(resolveLeaveLabel(leave.getResolvedLeaveName()))
                        .year(leave.getYear())
                        .managerComment(leave.getManagerComment())
                        .requestDate(leave.getRequestDate())
                        .build())
                .collect(Collectors.toList());
    }


    @Transient
    public String getResolvedLeaveTypeId(LeaveRequest leaveRequest) {
        if (leaveRequest.getLeaveType() != null) return leaveRequest.getLeaveType().getLeaveTypeId();
        if (leaveRequest.getGenderBasedLeaveType() != null) return leaveRequest.getGenderBasedLeaveType().getLeaveTypeId();
        return null;
    }
//
//    @Transient
//    public String getResolvedLeaveName() {
//        if (genderBasedLeaveType != null) return genderBasedLeaveType.getLeaveName();
//        if (leaveType != null) return leaveType.getLeaveName();
//        return null;
//    }
    
    @Override
    public List<LeaveRequestDTO> getAllLeaveRequestsExceptCancelled(String empId, Integer month, Integer year) {
        LocalDate today = LocalDate.now();
        int targetMonth = (month != null) ? month : today.getMonthValue();
        int targetYear = (year != null) ? year : today.getYear();
        
        LocalDate monthStart = LocalDate.of(targetYear, targetMonth, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        List<LeaveRequest> leaves = leaveRequestRepo.findActiveNonCancelledLeavesForMonth(empId, monthStart, monthEnd);
        
//        if (leaves.isEmpty()) {
//            throw new LeaveBalanceExceptionHandler("No leave requests found for the specified period");
//        }

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
        
//        if (leaves.isEmpty()) {
//            throw new LeaveBalanceExceptionHandler("No leave requests found for the specified period");
//        }

        return leaves.stream()
                .map(LeaveRequestDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<EmployeeApprovedLeavesDTO> getAllApprovedLeavesByYearGroupedByEmployee(Integer year) {
        List<LeaveRequest> approvedLeaves = leaveRequestRepo.findAllApprovedLeavesByYear(year);
        List<Holidays> holidays = holidaysService.getHolidaysByYear(year).getBody();
        Set<LocalDate> holidayDates = holidays != null ? 
            holidays.stream().map(Holidays::getHolidayDate).collect(java.util.stream.Collectors.toSet()) : 
            java.util.Collections.emptySet();
        
        return approvedLeaves.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    leave -> leave.getEmployee().getEmployeeId(),
                    java.util.stream.Collectors.mapping(
                        leave -> leave.getEmployee().getFullName(),
                        java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toList(),
                            names -> names.get(0)
                        )
                    )
                ))
                .entrySet().stream()
                .map(entry -> {
                    String employeeId = entry.getKey();
                    String employeeName = entry.getValue();
                    
                    List<LocalDate> approvedLeaveDates = approvedLeaves.stream()
                            .filter(leave -> leave.getEmployee().getEmployeeId().equals(employeeId))
                            .flatMap(leave -> {
                                List<LocalDate> dates = new ArrayList<>();
                                LocalDate current = leave.getStartDate();
                                while (!current.isAfter(leave.getEndDate())) {
                                    // Exclude weekends and holidays
                                    if (current.getDayOfWeek() != DayOfWeek.SATURDAY && 
                                        current.getDayOfWeek() != DayOfWeek.SUNDAY && 
                                        !holidayDates.contains(current)) {
                                        dates.add(current);
                                    }
                                    current = current.plusDays(1);
                                }
                                return dates.stream();
                            })
                            .distinct()
                            .sorted()
                            .collect(java.util.stream.Collectors.toList());
                    
                    return new EmployeeApprovedLeavesDTO(employeeId, employeeName, approvedLeaveDates);
                })
                .sorted(java.util.Comparator.comparing(EmployeeApprovedLeavesDTO::getEmployeeId))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public EmployeeApprovedLeavesDTO getApprovedLeavesByYearForEmployee(String employeeId, Integer year) {
        List<LeaveRequest> approvedLeaves = leaveRequestRepo.findApprovedLeavesByEmployeeAndYear(employeeId, year);
        
        if (approvedLeaves.isEmpty()) {
            return null;
        }
        
        String employeeName = approvedLeaves.get(0).getEmployee().getFullName();
        List<Holidays> holidays = holidaysService.getHolidaysByYear(year).getBody();
        Set<LocalDate> holidayDates = holidays != null ?
            holidays.stream().map(Holidays::getHolidayDate).collect(java.util.stream.Collectors.toSet()) : 
            java.util.Collections.emptySet();
        
        List<LocalDate> approvedLeaveDates = approvedLeaves.stream()
                .flatMap(leave -> {
                    List<LocalDate> dates = new ArrayList<>();
                    LocalDate current = leave.getStartDate();
                    while (!current.isAfter(leave.getEndDate())) {
                        // Exclude weekends and holidays
                        if (current.getDayOfWeek() != DayOfWeek.SATURDAY && 
                            current.getDayOfWeek() != DayOfWeek.SUNDAY && 
                            !holidayDates.contains(current)) {
                            dates.add(current);
                        }
                        current = current.plusDays(1);
                    }
                    return dates.stream();
                })
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        
        return new EmployeeApprovedLeavesDTO(employeeId, employeeName, approvedLeaveDates);
    }

    @Override
    public List<LeaveRequest> leaveBalanceViewDetails(String employeeId, String leaveName, Integer year) {
        List<LeaveRequest> request = leaveRequestRepo.findApprovedOrPendingByEmployeeAndLeaveNameAndYear(employeeId, leaveName, year);
        return request;
    }

}
