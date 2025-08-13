package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.Employee;

import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import jdk.jfr.Description;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * @author paves
 */
@CrossOrigin
@RestController
@RequestMapping("/api/leave-balance")
@RequiredArgsConstructor
public class LeaveBalanceController {

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceService;

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

    @PutMapping("/update-leave-balance-employee")
    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
    }

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
    public ResponseEntity<String> updateLeave(@RequestBody LeaveBalanceUpdateRequest request){
        return leaveBalanceService.updateLeaveBalancesFromHr(request);
    }

    @PostMapping("/trigger-monthly-process")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> triggerMonthlyProcess() {
        leaveBalanceService.triggerMonthlyLeaveAccrual();
        return ResponseEntity.ok("Monthly process triggered successfully.");
    }
}
