package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveRequest;

public interface LeaveWorkflowService {
    void startLeaveWorkflow(LeaveRequest leaveRequest);
    void processApprovalAction(String leaveId, String approverId, String decision);
}
