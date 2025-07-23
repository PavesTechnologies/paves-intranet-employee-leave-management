package com.paves.employee_leave_management.service;

/// 6. LeaveValidationServiceImpl.java (in service package)

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.serviceInterface.LeaveValidationServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveValidationServiceImpl implements LeaveValidationServiceInterface {

    @Autowired
    private MockDataService mockDataService;

    @Override
    public ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request) {
        ValidationResultDTO result = ValidationResultDTO.builder()
                .isValid(true)
                .employeeId(request.getEmployeeId())
                .requestedDays(request.getDaysRequested())
                .build();

        // Get employee and leave type info
        Employee employee = mockDataService.getEmployeeById(request.getEmployeeId());
        LeaveType leaveType = mockDataService.getLeaveTypeById(request.getLeaveTypeId());

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

        // Validate days requested matches date range
        long calculatedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (request.getDaysRequested() != calculatedDays) {
            result.addError(String.format("Days requested (%d) doesn't match date range (%d days)",
                    request.getDaysRequested(), calculatedDays));
        }
    }

    private void validateBalance(LeaveRequestValidationDTO request, ValidationResultDTO result,
                                 Employee employee, LeaveType leaveType) {
        Integer currentYear = LocalDate.now().getYear();
        LeaveBalanceDTO balance = mockDataService.getLeaveBalance(
                request.getEmployeeId(), request.getLeaveTypeId(), currentYear);

        if (balance == null) {
            result.addError("Leave balance not found for the current year");
            return;
        }

        result.setAvailableBalance(balance.getAvailableBalance());

        // Check if employee has sufficient leave balance
        if (!leaveType.getAllowNegativeBalance() &&
                balance.getAvailableBalance() < request.getDaysRequested()) {
            result.addError(String.format(
                    "Insufficient %s balance. Available: %d days, Requested: %d days",
                    leaveType.getLeaveName(), balance.getAvailableBalance(), request.getDaysRequested()));
        }

        // Check waiting period for new employees
        if (leaveType.getWaitingPeriodDays() != null && leaveType.getWaitingPeriodDays() > 0) {
            LocalDate eligibleDate = employee.getHireDate().plusDays(leaveType.getWaitingPeriodDays());
            if (LocalDate.now().isBefore(eligibleDate)) {
                result.addError(String.format(
                        "Employee not eligible for %s. Waiting period: %d days from hire date",
                        leaveType.getLeaveName(), leaveType.getWaitingPeriodDays()));
            }
        }
    }

    private void validateOverlaps(LeaveRequestValidationDTO request, ValidationResultDTO result) {
        List<LeaveRequest> overlappingRequests = mockDataService.getOverlappingRequests(
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

        // Validate notice period restriction
//        if (leaveType.getNoticePeriodRestriction()) {
//            // Mock check - in real implementation, check if employee is in notice period
//            // For now, we'll skip this validation
//        }

        // Validate half-day restrictions
        if (!leaveType.getAllowHalfDay() && request.getDaysRequested() < 1) {
            result.addError(String.format("%s does not allow half-day leave", leaveType.getLeaveName()));
        }
    }

    @Override
    public LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        return mockDataService.getLeaveBalance(employeeId, leaveTypeId, year);
    }

    @Override
    public List<LeaveRequest> getOverlappingRequests(String employeeId, String leaveTypeId,
                                                     LocalDate startDate, LocalDate endDate) {
        return mockDataService.getOverlappingRequests(employeeId, startDate, endDate);
    }

    @Override
    public boolean hasManagerApprovalRights(String managerId, String employeeId) {
        return mockDataService.isManager(managerId, employeeId);
    }
}