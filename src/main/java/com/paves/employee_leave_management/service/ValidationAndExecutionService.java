package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import com.paves.employee_leave_management.serviceInterface.ValidationAndExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ValidationAndExecutionService implements ValidationAndExecution {

    private final GenderBasedRepo genderBasedRepo;
    private GenderBasedLeaveBalanceServiceInterface leaveBalanceService;
     private final GenderBasedLeaveServiceInterface genderBasedLeaveService;
    private GenderBasedLeaveBalancesRepo leaveBalanceRepo;
    private ApprovalServiceInterface approvalRequestService;

    public ValidationAndExecutionService(GenderBasedLeaveServiceInterface genderBasedLeaveService,
                                         GenderBasedRepo genderBasedRepo,
                                         GenderBasedLeaveBalanceServiceInterface leaveBalanceService,
                                         GenderBasedLeaveBalancesRepo leaveBalanceRepo,
                                         ApprovalServiceInterface approvalRequestService) {
        this.genderBasedLeaveService = genderBasedLeaveService;
        this.genderBasedRepo = genderBasedRepo;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveBalanceRepo = leaveBalanceRepo;
        this.approvalRequestService = approvalRequestService;

    }

    @Override
    public ApiResponse<Object> validateGenderBaseLeave(GenderBasedLeave genderBaseLeave, Employee maker, String makerRole) {

        Optional<GenderBasedLeave> existing = genderBasedRepo.findByLeaveNameIgnoreCase(genderBaseLeave.getLeaveName());

        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActive())) {
            return new ApiResponse<>(false,
                    "Leave type already exists and is active",
                    null
            );
        }

        if("SUPER_ADMIN".equalsIgnoreCase(makerRole)){
            ResponseEntity<ApiResponse<Object>> response =  genderBasedLeaveService.createGenderBasedDirectly(genderBaseLeave, maker);
            return response.getBody();
        }

        // Send approval request
        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.CREATE_GENDER_BASED_LEAVE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("newData", genderBaseLeave);
        dto.setPayload(payload);

        approvalRequestService.submitForApproval(dto, maker, makerRole);

        return new ApiResponse<>(true,
                "Request submitted for approval",
                null
        );
    }

    @Override
    public ApiResponse<Object> updateValidateGenderBaseLeaveBalance(GenderBasedLeaveBalance request, Employee maker, String makerRole) {

        // ✅ Fetch current year balances (before state)
        List<GenderBasedLeaveBalance> beforeBalances =
                leaveBalanceService.getCurrentYearBalances(request.getEmployeeId());

        if (beforeBalances == null || beforeBalances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler(
                    "No leave balances found for current year for employee: " + request.getEmployeeId());
        }

        // ✅ Shape it exactly as required
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("employeeId", request.getEmployeeId());
        oldData.put("balances", beforeBalances);
//        oldData.put("performedBy", null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("oldData", oldData);
        payload.put("newData", request);

        // ✅ Build Approval DTO
        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.UPDATE_EMPLOYEE_LEAVE_BALANCE);
        dto.setPayload(payload);

        // ✅ Submit for approval
        approvalRequestService.submitForApproval(dto, maker, makerRole);

        return new ApiResponse<>(
                true,
                "Request to update leave balances has been submitted for approval.",
                null
        );
    }

}
