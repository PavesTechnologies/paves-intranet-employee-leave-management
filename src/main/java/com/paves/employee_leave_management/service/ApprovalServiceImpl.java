package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.ApproverType;
import com.paves.employee_leave_management.enums.RequestStatus;
import com.paves.employee_leave_management.repo.ApprovalRequestRepository;
import com.paves.employee_leave_management.repo.ApprovalRuleRepository;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.FunctionalApproverRepository;
import com.paves.employee_leave_management.serviceInterface.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApprovalServiceImpl implements ApprovalServiceInterface {

    @Autowired
    private ApprovalRuleRepository approvalRuleRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private FunctionalApproverRepository functionalApproverRepository;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private LeaveTypeServiceInterface leaveTypeService;

    @Autowired
    private LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HolidaysServiceInterface holidaysService;

    @Override
    @Transactional
    public void submitForApproval(MCApprovalRequestDto dto, Employee maker, String makerRole) {
        List<ApprovalRule> rules = approvalRuleRepository.findByActionTypeAndMakerRole(dto.getActionType(), makerRole);

        if (rules.isEmpty()) {
            throw new IllegalStateException("No approval rule found for action: " + dto.getActionType() + " and maker role: " + makerRole);
        }

        String workflowId = UUID.randomUUID().toString();

        for (ApprovalRule rule : rules) {
            ApprovalRequest request = new ApprovalRequest();
            request.setWorkflowId(workflowId);
            request.setRule(rule);
            request.setMakerId(Long.parseLong(maker.getEmployeeId()));
            request.setCreatedAt(LocalDateTime.now());

            Long approverId = determineApproverId(rule, maker);
            request.setApproverId(approverId);

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

    private Long determineApproverId(ApprovalRule rule, Employee maker) {
        ApproverType approverType = rule.getApproverType();

        switch (approverType) {
            case DIRECT_MAPPING:
                String fieldName = rule.getCheckerRole();
                switch (fieldName) {
                    case "manager":
                        Employee manager = maker.getManager();
                        if (manager == null) {
                            throw new IllegalStateException("Approval Error: Employee " + maker.getFullName() + " does not have a manager assigned.");
                        }
                        return Long.parseLong(manager.getEmployeeId());
                    case "hr_administrator":
                        Employee hrAdmin = maker.getHrAdministrator();
                        if (hrAdmin == null) {
                            throw new IllegalStateException("Approval Error: Employee " + maker.getFullName() + " does not have an HR Administrator assigned.");
                        }
                        return Long.parseLong(hrAdmin.getEmployeeId());
                    default:
                        throw new IllegalStateException("Configuration Error: Unsupported checker role for DIRECT_MAPPING: " + fieldName);
                }

            default:
                throw new UnsupportedOperationException("The approver type '" + approverType + "' is not currently supported.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequestResponseDto> getPendingApprovalsForUser(Employee approver) {
        List<ApprovalRequest> requests = approvalRequestRepository.findByApproverIdAndStatus(Long.parseLong(approver.getEmployeeId()), RequestStatus.PENDING);

        return requests.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private ApprovalRequestResponseDto mapToDto(ApprovalRequest request) {
        ApprovalRequestResponseDto dto = new ApprovalRequestResponseDto();
        dto.setId(request.getId());
        dto.setActionType(request.getRule().getActionType());
        dto.setStatus(request.getStatus());
        dto.setPayload(request.getPayload());
        dto.setCreatedAt(request.getCreatedAt());

        Employee maker = employeeRepo.findById(String.valueOf(request.getMakerId()))
                .orElse(null);
        if (maker != null) {
            dto.setMakerName(maker.getFullName());
        }

        return dto;
    }


    @Override
    @Transactional
    public void approveRequest(Long requestId, ApproveRequestDto dto, Employee checker) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not in a pending state.");
        }
        if (!request.getApproverId().equals(Long.parseLong(checker.getEmployeeId()))) {
            throw new IllegalStateException("You are not authorized to approve this request.");
        }

        request.setStatus(RequestStatus.APPROVED);
        request.setResolvedAt(LocalDateTime.now());

        // Check if this is the final approval level before executing the business logic
        boolean isFinalApproval = approvalRequestRepository
                .findByWorkflowIdAndRule_ApprovalLevel(request.getWorkflowId(), request.getRule().getApprovalLevel() + 1)
                .isEmpty();

        if (isFinalApproval) {
            executeBusinessLogic(request);
        }

        approvalRequestRepository.save(request);

        if (!isFinalApproval) {
            activateNextApprovalLevel(request);
        }
    }

    @Override
    @Transactional
    public void rejectRequest(Long requestId, RejectRequestDto dto, Employee checker) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not in a pending state.");
        }
        if (!request.getApproverId().equals(Long.parseLong(checker.getEmployeeId()))) {
            throw new IllegalStateException("You are not authorized to reject this request.");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(dto.getReason());
        request.setResolvedAt(LocalDateTime.now());

        approvalRequestRepository.save(request);

        cancelSubsequentApprovals(request);
    }

    private void executeBusinessLogic(ApprovalRequest request) {
        ActionType actionType = request.getRule().getActionType();
        String payload = request.getPayload();

        try {
            switch (actionType) {
                case CREATE_LEAVE_TYPE:
                    Map<String, Object> createPayload = objectMapper.readValue(payload, Map.class);
                    LeaveType newLeaveType = objectMapper.convertValue(createPayload.get("newData"), LeaveType.class);
                    leaveTypeService.addLeaveType(newLeaveType);
                    break;
                case UPDATE_LEAVE_TYPE:
                    Map<String, Object> updatePayload = objectMapper.readValue(payload, Map.class);
                    LeaveType updatedLeaveType = objectMapper.convertValue(updatePayload.get("after"), LeaveType.class);
                    leaveTypeService.updateLeaveType(updatedLeaveType, updatedLeaveType.getLeaveTypeId());
                    break;
                case DEACTIVATE_LEAVE_TYPE:
                    Map<String, String> deactivatePayload = objectMapper.readValue(payload, Map.class);
                    String leaveTypeId = deactivatePayload.get("leaveTypeId");
                    LocalDate effectiveDate = LocalDate.parse(deactivatePayload.get("deactivationEffectiveDate"));
                    leaveTypeService.deActiveLeaveType(leaveTypeId, effectiveDate);
                    break;
                case UPDATE_EMPLOYEE_LEAVE_BALANCE:
                    Map<String, Object> balanceUpdatePayload = objectMapper.readValue(payload, Map.class);
                    LeaveBalanceUpdateRequest balanceUpdateRequest = objectMapper.convertValue(balanceUpdatePayload.get("newData"), LeaveBalanceUpdateRequest.class);
                    leaveBalanceService.updateLeaveBalancesFromHr(balanceUpdateRequest);
                    break;
                case ADD_HOLIDAY:
                    Map<String, Object> holidayPayload = objectMapper.readValue(payload, Map.class);
                    List<Holidays> holidays = objectMapper.convertValue(holidayPayload.get("newData"), new TypeReference<List<Holidays>>() {
                    });
                    holidaysService.addHoliday(holidays);
                    break;
                case UPDATE_HOLIDAY:
                    Map<String, Object> updateHolidayPayloadMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
                    Holidays updatedHolidayData = objectMapper.convertValue(
                            updateHolidayPayloadMap.get("requestedState"), Holidays.class
                    );
                    // --- CALL EXISTING HOLIDAY SERVICE METHOD ---
                    ResponseEntity<String> updateResponse = holidaysService.updateHoliday(updatedHolidayData);
                    if (!updateResponse.getStatusCode().is2xxSuccessful()) {
                        throw new RuntimeException("HolidaysService.updateHoliday failed: " + updateResponse.getBody());
                    }
//                    log.info("Successfully updated Holiday ID {} via approved workflow.", updatedHolidayData.getHolidayId());
//                    success = true;
                    break;
                case DELETE_HOLIDAY:
                    // Parse the payload as a Map
                    Map<String, Object> deleteHolidayPayload = objectMapper.readValue(
                            payload,
                            new TypeReference<Map<String, Object>>() {}
                    );

                    // Extract nested holiday object
                    Map<String, Object> holidayMap = (Map<String, Object>) deleteHolidayPayload.get("holiday");
                    if (holidayMap == null) {
                        throw new IllegalArgumentException("Payload missing holiday details");
                    }

                    Long holidayIdToDelete = ((Number) holidayMap.get("holidayId")).longValue();
                    if (holidayIdToDelete == null) {
                        throw new IllegalArgumentException("Holiday ID is missing in payload");
                    }

                    // --- CALL EXISTING HOLIDAY SERVICE METHOD ---
                    ResponseEntity<String> deleteResponse = holidaysService.deleteHoliday(holidayIdToDelete);
                    if (!deleteResponse.getStatusCode().is2xxSuccessful()) {
                        throw new RuntimeException("HolidaysService.deleteHoliday failed: " + deleteResponse.getBody());
                    }

                    // log.info("Successfully deleted Holiday ID {} via approved workflow.", holidayIdToDelete);
                    break;
                default:
                    throw new IllegalStateException("Unsupported action type: " + actionType);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing payload for action: " + actionType, e);
        }
    }

    private void activateNextApprovalLevel(ApprovalRequest approvedRequest) {
        int nextLevel = approvedRequest.getRule().getApprovalLevel() + 1;
        approvalRequestRepository
                .findByWorkflowIdAndRule_ApprovalLevel(approvedRequest.getWorkflowId(), nextLevel)
                .ifPresent(nextRequest -> {
                    if (nextRequest.getStatus() == RequestStatus.WAITING) {
                        nextRequest.setStatus(RequestStatus.PENDING);
                        approvalRequestRepository.save(nextRequest);
                    }
                });
    }

    private void cancelSubsequentApprovals(ApprovalRequest rejectedRequest) {
        List<ApprovalRequest> waitingRequests = approvalRequestRepository
                .findByWorkflowIdAndStatus(rejectedRequest.getWorkflowId(), RequestStatus.WAITING);

        for (ApprovalRequest request : waitingRequests) {
            request.setStatus(RequestStatus.CANCELED);
            approvalRequestRepository.save(request);
        }
    }
}
