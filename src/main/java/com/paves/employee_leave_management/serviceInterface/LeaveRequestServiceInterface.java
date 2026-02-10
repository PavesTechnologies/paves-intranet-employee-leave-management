package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.LeaveRequest;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestServiceInterface {
    // Manager operations - New DTO-based methods
    List<LeaveRequest> getRequestsForManager(ManagerQueryDTO queryDTO);

    List<LeaveRequest> getLeaveHistoryForManager(ManagerQueryDTO queryDTO);

    LeaveRequest approveRequest(ApprovalRequestDTO approvalRequest);

    LeaveRequest rejectRequest(RejectionRequestDTO rejectionRequest);

    LeaveRequest updateLeaveRequestByManager(ManagerUpdateRequestDTO updateRequest);

    // Employee operations
    ValidationResultDTO updateRequestByEmployee(LeaveRequest leaveRequest, LeaveRequestValidationDTO request);

    List<LeaveRequest> getLeaveRequestsByEmployee(String employeeId);

    List<LeaveRequest> getLeaveRequestsByEmployeeAndByYear(String employeeId, int year);

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

    List<LeaveRequest> getLeaveHistoryByYear(String employeeId, LocalDate startDate, LocalDate endDate);

    List<LeaveRequest> approveMultipleRequests(@Valid BatchApprovalRequestDTO batchApproval);

    List<LeaveRequest> rejectMultipleRequests(@Valid BatchApprovalRequestDTO batchApproval);

    List<PendingAndApprovedLeaveRequestsDTO> getPendingLeaveAndApprovedLeaveByEmployeeId(String employeeId, LocalDate startDate, LocalDate endDate);

    List<LeaveRequest> leaveBalanceViewDetails(String employeeId, String leaveName, Integer year);

    List<LeaveRequest> getPendingLeaveRequestsByEmployee(String employeeId);

    List<LeaveRequest> getPendingLeaveRequestsByEmployeeAndYear(String employeeId, int year);
    
    List<LeaveRequestDTO> getAllLeaveRequestsExceptCancelled(String employeeId, Integer month, Integer year);
    
    List<LeaveRequestDTO> getAllEmployeesLeaveRequestsByMonthYear(Integer month, Integer year);
    
    List<EmployeeApprovedLeavesDTO> getAllApprovedLeavesByYearGroupedByEmployee(Integer year);
    
    EmployeeApprovedLeavesDTO getApprovedLeavesByYearForEmployee(String employeeId, Integer year);
}
