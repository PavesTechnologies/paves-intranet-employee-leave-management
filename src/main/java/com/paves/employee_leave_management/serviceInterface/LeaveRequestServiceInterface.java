package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.LeaveRequest;

import java.util.List;

public interface LeaveRequestServiceInterface {
    List<LeaveRequest> getPendingRequestsForManager(String managerId);
    LeaveRequest approveRequest(String leaveId,String managerId);
    LeaveRequest rejectRequest(String leaveId ,String managerId,String comment);

}
