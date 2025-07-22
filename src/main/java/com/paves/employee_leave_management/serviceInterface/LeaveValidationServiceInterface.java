package com.paves.employee_leave_management.serviceInterface;


import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import java.util.List;

public interface LeaveValidationServiceInterface {
    ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request);
    ValidationResultDTO validateLeaveRequestEntity(LeaveRequest request);
    LeaveBalanceDTO getEmployeeLeaveBalance(String employeeId, String leaveTypeId, Integer year);
    List<LeaveRequest> getOverlappingRequests(String employeeId, String leaveTypeId,
                                              java.time.LocalDate startDate, java.time.LocalDate endDate);
    boolean hasManagerApprovalRights(String managerId, String employeeId);
}
