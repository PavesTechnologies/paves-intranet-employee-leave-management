package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
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
    private ApprovalServiceInterface approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private SimpMessagingTemplate template;

//    @Autowired
//    LeaveBalanceDAO leaveBalanceDao;

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
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<LeaveBalance>> getAllLeaveBalances() {
        return leaveBalanceService.getAllLeaveBalances();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByEmployeeId(@PathVariable String employeeId) {
        template.convertAndSend("/topic/data-updated", "updated");
        return leaveBalanceService.findByEmployeeId(employeeId);
    }
    
    @GetMapping("/employee/{employeeId}/{year}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasAnyRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByEmployeeIdAndYear(
            @PathVariable String employeeId, 
            @PathVariable Integer year) {
        template.convertAndSend("/topic/data-updated", "updated");
        return leaveBalanceService.findByEmployeeIdAndYear(employeeId, year);
    }

//    @PutMapping("/update-leave-balance-employee")
//    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
//        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
//    }

    @GetMapping("/type/{leaveTypeId}")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByLeaveName(@PathVariable String leaveTypeId) {
        return leaveBalanceService.findByLeaveId(leaveTypeId);
    }

    @PutMapping("/update-leave-balance-employee")
    @PreAuthorize("@permissionService.isOwner(authentication, #leaveBalance[0].employee.employeeId)")
    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
    }

    @PostMapping("/update-leave-balance")
    @PreAuthorize("hasAnyRole('HR')")
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
        String makerRole = "HR"; // or maker.getJobTitle()

        // ✅ Fetch current year balances (before state)
        List<LeaveBalance> beforeBalances =
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
        approvalService.submitForApproval(dto, maker, makerRole);

        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Request to update leave balances has been submitted for approval.",
                null
        ));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<LeaveBalance>> search(@RequestParam(value = "query", required = false) String query) {
        List<LeaveBalance> results = leaveBalanceService.searchLeaveBalances(query);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/autocomplete")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<String>> autocomplete(@RequestParam("query") String query) {
        List<String> suggestions = leaveBalanceService.autocompleteEmployee(query);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> monthly() {
        leaveBalanceService.processAccrualForLeaveType();
        return ResponseEntity.ok("Monthly process triggered successfully.");
    }

}
