package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.service.ApprovalService;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author paves
 */
@CrossOrigin
@RestController
@RequestMapping("/api/leave-balance")
@RequiredArgsConstructor
public class LeaveBalanceController {

    @Autowired
    private LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    LeaveBalanceDAO leaveBalanceDao;

    // This is a placeholder for getting the user from the JWT token
    private Employee getAuthenticatedUser() {
        // In a real application, you would extract the user details from the Spring Security Context.
        // For now, we'll fetch a hardcoded user to simulate this.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            // You can fetch using email or user_id depending on your DB
//            String email = jwt.getClaim("email");  // "employee1@example.com"
            Long userId = jwt.getClaim("user_id"); // If needed

            return employeeRepo.findByEmployeeId(String.valueOf(userId))
                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
        }

        throw new RuntimeException("Invalid authentication principal");
    }


    @PostMapping("/generate/{employeeId}")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> generateLeaveBalance(@PathVariable String employeeId) {
        leaveBalanceService.createLeaveBalanceForNewEmployee(employeeId);
        return ResponseEntity.ok("Leave balance generated successfully for employee: " + employeeId);
    }

    @PostMapping("/carryforward")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> carryForward() {
        leaveBalanceService.processYearEndCarryForward();
        return ResponseEntity.ok("Carry forward process completed.");
    }

    @GetMapping("/{balanceID}")
    @PreAuthorize("hasAnyRole('MANAGER','HR','GENERAL')")
    public ResponseEntity<LeaveBalance> getLeaveBalancesByBalanceId(@PathVariable String balanceID) {
        return leaveBalanceService.findByBalanceId(balanceID);
    }

    @GetMapping("/all-leave-balances")
    @PreAuthorize("hasAnyRole('MANAGER','HR','GENERAL')")
    public ResponseEntity<List<LeaveBalance>> getAllLeaveBalances() {
        return leaveBalanceService.getAllLeaveBalances();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('GENERAL','MANAGER','HR')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByEmployeeId(@PathVariable String employeeId) {
        return leaveBalanceService.findByEmployeeId(employeeId);
    }

//    @PutMapping("/update-leave-balance-employee")
//    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
//        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
//    }

    @GetMapping("/type/{leaveTypeId}")
    @PreAuthorize("hasAnyRole('MANAGER','HR','GENERAL')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByLeaveName(@PathVariable String leaveTypeId) {
        return leaveBalanceService.findByLeaveId(leaveTypeId);
    }

    @PutMapping("/update-leave-balance-employee")
    @PreAuthorize("hasRole('GENERAL')")
    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
    }

    @PostMapping("/update-leave-balance")
    public ResponseEntity<String> approveLeave(
            @RequestParam String employeeId,
            @RequestParam String leaveTypeId,
            @RequestParam double approvedDays
    ) {
        int currentYear = Year.now().getValue();
        leaveBalanceService.updateLeaveBalanceAfterApproval(employeeId, leaveTypeId, approvedDays, currentYear);
        return ResponseEntity.ok("Leave approved and balance updated successfully.");
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<ApiResponse<Object>> updateLeave(@RequestBody LeaveBalanceUpdateRequest request) {
        Employee maker = getAuthenticatedUser();
//        String makerRole = maker.getJobTitle();
        String makerRole = "HR";// Assuming role is in jobTitle

        List<LeaveBalance> beforeBalances = leaveBalanceDao.findByEmployeeId(request.getEmployeeId());

        if (beforeBalances == null || beforeBalances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler(
                    "Leave Balances not found for employee: " + request.getEmployeeId()
            );
        }


        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.UPDATE_EMPLOYEE_LEAVE_BALANCE);

        Map<String, Object> payload = new HashMap<>();
        // For now, we just pass the request data. A more advanced implementation
        // would fetch the 'before' state of the balances for a better audit trail.
        payload.put("beforeData", beforeBalances);
        payload.put("newData", request);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker, makerRole);

        return ResponseEntity.ok(new ApiResponse<>(true,"Request to update leave balances has been submitted for approval.",null));
    }

    @PostMapping("/trigger-monthly-process")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> triggerMonthlyProcess() {
        leaveBalanceService.triggerMonthlyLeaveAccrual();
        return ResponseEntity.ok("Monthly process triggered successfully.");
    }

    @GetMapping("/search")
    public ResponseEntity<List<LeaveBalance>> search(@RequestParam(value = "query", required = false) String query) {
        List<LeaveBalance> results = leaveBalanceService.searchLeaveBalances(query);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(@RequestParam("query") String query) {
        List<String> suggestions = leaveBalanceService.autocompleteEmployee(query);
        return ResponseEntity.ok(suggestions);
    }

}
