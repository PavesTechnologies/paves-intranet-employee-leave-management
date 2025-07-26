package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestServiceInterface {
    // Manager operations
    List<LeaveRequest> getPendingRequestsForManager(String managerId);
    List<LeaveRequest> getLeaveHistoryForManager(String managerId);
    LeaveRequest approveRequest(String leaveId, String managerId);
    LeaveRequest rejectRequest(String leaveId, String managerId, String comment);
    LeaveRequest updateLeaveRequestByManager(String leaveId, String managerId, String leaveTypeId, LocalDate startDate, LocalDate endDate);

    // Employee operations
    ValidationResultDTO updateRequest(LeaveRequest leaveRequest);
    List<LeaveRequest> getLeaveRequestsByEmployee(String employeeId);
    LeaveRequest getLeaveRequestById(String leaveId);
    LeaveRequest cancelLeaveRequest(String leaveId, String employeeId);

    // Validation operations (merged from LeaveValidationServiceInterface)
    ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request);
    ValidationResultDTO validateLeaveRequestEntity(LeaveRequest request);
    
    // Application operations (merged from LeaveApplicationService)
    LeaveRequest saveLeaveRequest(LeaveRequestValidationDTO request);
    
    // Utility operations
    LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year);
    List<LeaveRequest> getOverlappingRequests(String employeeId, LocalDate startDate, LocalDate endDate);
}
