package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.ApproveRequestDto;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.dto.RejectRequestDto;
import com.paves.employee_leave_management.entities.ApprovalRequest;
import com.paves.employee_leave_management.entities.ApprovalRule;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.RequestStatus;
import com.paves.employee_leave_management.repository.ApprovalRequestRepository;
import com.paves.employee_leave_management.repository.ApprovalRuleRepository;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ApprovalServiceImpl implements ApprovalService {

    @Autowired
    private ApprovalRuleRepository approvalRuleRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private LeaveTypeServiceInterface leaveTypeService;

    @Autowired
    private LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public void submitForApproval(MCApprovalRequestDto dto, Employee maker) {
        String makerRole = "HR-Manager"; // Example role

        List<ApprovalRule> rules = approvalRuleRepository.findByActionTypeAndMakerRole(dto.getActionType(), makerRole);

        for (ApprovalRule rule : rules) {
            ApprovalRequest request = new ApprovalRequest();
            request.setRule(rule);
            request.setMakerId(Long.parseLong(maker.getEmployeeId()));
            request.setCreatedAt(LocalDateTime.now());

            // For now, we'll leave the approver null. This will be determined by the rule.
            // request.setApproverId(determineApprover(rule, maker));

            if (rule.getApprovalLevel() == 1) {
                request.setStatus(RequestStatus.PENDING);
            } else {
                request.setStatus(RequestStatus.WAITING);
            }

            try {
                request.setPayload(objectMapper.writeValueAsString(dto.getPayload()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing payload", e);
            }

            approvalRequestRepository.save(request);
        }
    }

    @Override
    public List<ApprovalRequest> getPendingApprovalsForUser(Employee approver) {
        return approvalRequestRepository.findByApproverIdAndStatus(Long.parseLong(approver.getEmployeeId()), RequestStatus.PENDING);
    }

    @Override
    @Transactional
    public void approveRequest(Long requestId, ApproveRequestDto dto, Employee checker) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));

        // Basic validation
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not in a pending state.");
        }
        // More validation needed here to check if 'checker' is the correct approver

        request.setStatus(RequestStatus.APPROVED);
        request.setResolvedAt(LocalDateTime.now());

        // Execute business logic based on action type
        executeBusinessLogic(request);

        approvalRequestRepository.save(request);

        // Activate next level of approval if it exists
        activateNextApprovalLevel(request);
    }

    @Override
    @Transactional
    public void rejectRequest(Long requestId, RejectRequestDto dto, Employee checker) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not in a pending state.");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(dto.getReason());
        request.setResolvedAt(LocalDateTime.now());

        approvalRequestRepository.save(request);

        // Cancel any subsequent WAITING requests in the same workflow
        cancelSubsequentApprovals(request);
    }

    private void executeBusinessLogic(ApprovalRequest request) {
        ActionType actionType = request.getRule().getActionType();
        String payload = request.getPayload();

        try {
            switch (actionType) {
                case CREATE_LEAVE_TYPE:
                    LeaveType newLeaveType = objectMapper.readValue(payload, LeaveType.class);
                    leaveTypeService.addLeaveType(newLeaveType);
                    break;
                case UPDATE_LEAVE_TYPE:
                    // Assuming payload contains the full updated LeaveType object
                    LeaveType updatedLeaveType = objectMapper.readValue(payload, LeaveType.class);
                    leaveTypeService.updateLeaveType(updatedLeaveType, updatedLeaveType.getLeaveTypeId());
                    break;
                case DEACTIVATE_LEAVE_TYPE:
                    // Assuming payload contains a map with "leaveTypeId"
                    Map<String, String> deactivatePayload = objectMapper.readValue(payload, Map.class);
                    leaveTypeService.deActiveLeaveType(deactivatePayload.get("leaveTypeId"));
                    break;
                // case UPDATE_EMPLOYEE_LEAVE_BALANCE:
                //     LeaveBalanceUpdateRequest balanceUpdateRequest = objectMapper.readValue(payload, LeaveBalanceUpdateRequest.class);
                //     leaveBalanceService.updateLeaveBalancesFromHr(balanceUpdateRequest);
                //     break;
                default:
                    throw new IllegalStateException("Unsupported action type: " + actionType);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing payload for action: " + actionType, e);
        }
    }

    private void activateNextApprovalLevel(ApprovalRequest approvedRequest) {
        // Find if there is a rule for the next level
        // This is a simplified example. A more robust implementation would be needed.
    }

    private void cancelSubsequentApprovals(ApprovalRequest rejectedRequest) {
        // Find and cancel any WAITING requests that are part of the same workflow
    }
}
