package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.LeaveRevoke;

public interface LeaveRevokeRequest {
    public String newRevokeRequest(LeaveRevoke revokeRequest);
}
