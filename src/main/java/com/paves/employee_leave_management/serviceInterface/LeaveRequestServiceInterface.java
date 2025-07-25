package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestServiceInterface {
    List<LeaveRequest> getPendingRequestsForManager(String managerId);
    List<LeaveRequest> getLeaveHistoryForManager(String managerId);
    LeaveRequest approveRequest(String leaveId,String managerId);
    LeaveRequest rejectRequest(String leaveId ,String managerId,String comment);


    LeaveRequest updateLeaveRequestByManager(String leaveId, String managerId, String leaveTypeId, LocalDate startDate, LocalDate endDate);

     ValidationResultDTO updateRequest(LeaveRequest leaveRequest);
}
