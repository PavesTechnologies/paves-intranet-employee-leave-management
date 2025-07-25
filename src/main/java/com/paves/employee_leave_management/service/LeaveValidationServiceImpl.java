package com.paves.employee_leave_management.service;

/// 6. LeaveValidationServiceImpl.java (in service package)

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveValidationServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveValidationServiceImpl implements LeaveValidationServiceInterface {

//    @Autowired
//    private MockDataService mockDataService;
      @Autowired
      EmployeeServiceInterface employeeService;

      @Autowired
      LeaveRequestRepo leaveRequestRepo;

      @Autowired
      LeaveTypeServiceInterface leaveTypeServiceInterface;

      @Autowired
      LeaveBalanceServiceInterface leaveBalanceServiceInterface;

//      @Autowired
//      private LeaveValidationServiceInterface leaveValidationService;

    @Override
    public ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO result = ValidationResultDTO.builder()
                .isValid(true)
                .employeeId(request.getEmployeeId())
                .requestedDays((float) request.getDaysRequested())
                .build();

        System.out.println(request);
        System.out.println(result);
        // Get employee and leave type info
        Employee employee = employeeService.getByEmployeeId(request.getEmployeeId()).getBody();
        LeaveType leaveType = leaveTypeServiceInterface.getLeaveTypeById(request.getLeaveTypeId()).getBody();
        System.out.println("**************************************");
        System.out.print(employee);
        System.out.print(leaveType);
        if (employee == null) {
            result.addError("Employee not found");
            return result;
        }

        if (leaveType == null) {
            result.addError("Leave type not found");
            return result;
        }

        result.setEmployeeName(employee.getFullName());

        // Validate dates
        // check advance Notice Days
        // for Paternity 5 days should be continuous
        // should allow past days limit upto the leave type limit for backdated leaves

        validateDates(request, result, leaveType);

        // Validate leave balance
        validateBalance(request, result, employee, leaveType);

        // Validate overlapping requests
        validateOverlaps(request, result);

        // Validate leave type specific rules
        validateLeaveTypeRules(request, result, employee, leaveType);




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
                .build();

        return validateLeaveRequest(dto);
    }

    private void validateDates(LeaveRequestValidationDTO request, ValidationResultDTO result, LeaveType leaveType) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        LocalDate today = LocalDate.now();
        System.out.println();
        System.out.println("From Validate Days +++++++++++++++++++");
        System.out.print(startDate);
        System.out.print(endDate);
        System.out.print(today);

        // End date must be after or equal to start date
        if (endDate.isBefore(startDate)) {
            result.addError("End date must be after or equal to start date");
        }

        // Check if dates are in the past beyond policy limits
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

        // Validate advance notice requirement
        if (leaveType.getAdvanceNoticeDays() != null && leaveType.getAdvanceNoticeDays() > 0) {
            long daysBetween = ChronoUnit.DAYS.between(today, startDate);
            if (daysBetween < leaveType.getAdvanceNoticeDays()) {
                result.addError(String.format("Leave request requires %d days advance notice",
                        leaveType.getAdvanceNoticeDays()));
            }
        }

        if (leaveType.getAdvanceNoticeDays() != null && leaveType.getAdvanceNoticeDays() > 0) {

            long daysBetween = ChronoUnit.DAYS.between(today, startDate);
            if (daysBetween < leaveType.getAdvanceNoticeDays()) {
                result.addError(String.format("Leave request requires at least %d days advance notice",
                        leaveType.getAdvanceNoticeDays()));
            }
        }

        System.out.println("valid");
    }

    private void validateBalance(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                 Employee employee, LeaveType leaveType) {
        Integer currentYear = LocalDate.now().getYear();
        System.out.println("***************************************");
        System.out.println(request);
        LeaveBalanceDTO balance = leaveBalanceServiceInterface.getLeaveBalance(
                request.getEmployeeId(), request.getLeaveTypeId(), currentYear);
        System.out.println("***************************************");
        System.out.print(balance);
        if (balance == null) {
            result.addError("Leave balance not found for the current year");
            return;
        }

//        result.setAvailableBalance(balance.getRemainingLeaves());

        // Check if employee has sufficient leave balance
        if (!leaveType.getAllowNegativeBalance() &&
                balance.getRemainingLeaves() < request.getDaysRequested()) {
            result.addError(String.format(
                    "Insufficient %s balance. Available: %.2f days, Requested: %.2f days",
                    leaveType.getLeaveName(), balance.getRemainingLeaves(), request.getDaysRequested()));
        }

        // Check waiting period for new employees
        if (!"L-UL".equalsIgnoreCase(leaveType.getLeaveTypeId())&& leaveType.getWaitingPeriodDays() != null && leaveType.getWaitingPeriodDays() > 0) {
            LocalDate eligibleDate = employee.getHireDate().plusDays(leaveType.getWaitingPeriodDays());
            if (LocalDate.now().isBefore(eligibleDate)) {
                result.addError(String.format(
                        "Employee not eligible for %s. Waiting period: %d days from hire date",
                        leaveType.getLeaveName(), leaveType.getWaitingPeriodDays()));
            }
        }
    }

    private void validateOverlaps(LeaveRequestValidationDTO request, ValidationResultDTO result) {
        List<LeaveRequest> overlappingRequests = getOverlappingRequests(
                request.getEmployeeId(), request.getStartDate(), request.getEndDate());

        for (LeaveRequest existing : overlappingRequests) {
            result.addError(String.format(
                    "Leave request overlaps with existing %s leave from %s to %s",
                    existing.getLeaveType().getLeaveName(),
                    existing.getStartDate(),
                    existing.getEndDate()));
        }
    }

    private void validateLeaveTypeRules(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                        Employee employee, LeaveType leaveType) {
        // Validate reason is provided (all leave types require comments as per business rules)
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            result.addError("Leave reason/comments are mandatory");
        }

        if ("L-ML".equalsIgnoreCase(request.getLeaveTypeId())) {

            // Only females can apply
//            if (!"Female".equalsIgnoreCase(employee.getGender())) {
//                result.addError("Maternity leave is only applicable for female employees.");
//                return;
//            }
            // Pending maternity leave check
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
        }

        // Validate notice period restriction
//        if (leaveType.getNoticePeriodRestriction()) {
//            // Mock check - in real implementation, check if employee is in notice period
//            // For now, we'll skip this validation
//        }
        if ("L-PL".equalsIgnoreCase(request.getLeaveTypeId())) {
            // Check prior approvals
            int pendingCount = leaveRequestRepo.countPendingLeavesByType(employee.getEmployeeId(), "L-PL");
            if (pendingCount > 0) {
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

//            // Must be within 21 days of child birth
//            if (request.getChildBirthDate() == null) {
//                result.addError("Child birth date is required for paternity leave.");
//            } else {
//                long daysSinceBirth = ChronoUnit.DAYS.between(request.getChildBirthDate(), request.getStartDate());
//                if (daysSinceBirth > 21) {
//                    result.addError("Paternity leave must be taken within 21 days of child birth.");
//                }
//            }

            // Check for 1-year gap if there’s a prior leave
            if (approvedPL.size() == 1) {
                LeaveRequest previousLeave = approvedPL.getFirst();
                long gap = ChronoUnit.DAYS.between(previousLeave.getStartDate(), request.getStartDate());
                if (gap < 365) {
                    result.addError("There must be a minimum 1-year gap between two paternity leaves.");
                }
            }
        }

        // Validate half-day restrictions
        if (!leaveType.getAllowHalfDay() && request.getDaysRequested() < 1) {
            result.addError(String.format("%s does not allow half-day leave", leaveType.getLeaveName()));
        }
    }

    @Override
    public LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        return leaveBalanceServiceInterface.getLeaveBalance(employeeId, leaveTypeId, year);
    }

    @Override
    public List<LeaveRequest> getOverlappingRequests(String employeeId, LocalDate startDate, LocalDate endDate) {
        return leaveRequestRepo.findOverlappingLeaves(employeeId, startDate, endDate);
    }

//    @Override
//    public boolean hasManagerApprovalRights(String managerId, String employeeId) {
//        return false;
//    }
//    @Override
//    public List<LeaveRequest> getOverlappingRequests(String employeeId, String leaveTypeId,
//                                                     LocalDate startDate, LocalDate endDate) {
//        return leaveRequestRepo.findOverlappingLeaves(employeeId, startDate, endDate);
//    }

//    @Override
//    public boolean hasManagerApprovalRights(String managerId, String employeeId) {
//        return mockDataService.isManager(managerId, employeeId);
//    }
}