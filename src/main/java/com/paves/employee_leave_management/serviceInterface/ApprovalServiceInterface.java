package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApprovalRequestResponseDto;
import com.paves.employee_leave_management.dto.ApproveRequestDto;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.dto.RejectRequestDto;
import com.paves.employee_leave_management.entities.Employee;

import java.util.List;

public interface ApprovalServiceInterface {

    void submitForApproval(MCApprovalRequestDto dto, Employee maker, String makerRole);

    List<ApprovalRequestResponseDto> getPendingApprovalsForUser(Employee approver);

    void approveRequest(Long requestId, ApproveRequestDto dto, Employee checker);

    void rejectRequest(Long requestId, RejectRequestDto dto, Employee checker);
}
