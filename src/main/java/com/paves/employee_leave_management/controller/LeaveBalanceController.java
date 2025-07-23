package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/leave-balance")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceServiceInterface leaveBalanceService;

    @PostMapping("/generate/{employeeId}")
    public ResponseEntity<String> generateLeaveBalance(@PathVariable String employeeId) {
        leaveBalanceService.createLeaveBalanceForNewEmployee(employeeId);
        return ResponseEntity.ok("Leave balance generated successfully for employee: " + employeeId);
    }

    @PostMapping("/carryforward")
    public ResponseEntity<String> carryForward() {
        leaveBalanceService.processYearEndCarryForward();
        return ResponseEntity.ok("Carry forward process completed.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveBalance> getById(@PathVariable String id) {
        return ResponseEntity.ok(leaveBalanceService.findByBalanceId(id));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveBalance>> getByEmployee(@PathVariable String employeeId) {
        return ResponseEntity.ok(leaveBalanceService.findByEmployeeId(employeeId));
    }

    @GetMapping("/leave-name/{name}")
    public ResponseEntity<List<LeaveBalance>> getByLeaveName(@PathVariable String name) {
        return ResponseEntity.ok(leaveBalanceService.findByLeaveName(name));
    }

    @GetMapping
    public ResponseEntity<List<LeaveBalance>> getAll() {
        return ResponseEntity.ok(leaveBalanceService.getAllLeaveBalances());
    }
}

