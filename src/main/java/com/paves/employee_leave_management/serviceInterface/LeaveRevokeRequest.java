package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveRevokeDTO;
import com.paves.employee_leave_management.dto.RevokeRequestDTO;
import com.paves.employee_leave_management.entities.LeaveRevoke;

import java.util.List;

public interface LeaveRevokeRequest {
    public String newRevokeRequest(LeaveRevoke revokeRequest);

    public void approveRequest(String id, RevokeRequestDTO revokeRequestDTO);

    public List<LeaveRevokeDTO> getPendingRequests(String managerId);

    public void rejectRequest(String id);
}
