package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.service.LeaveBalanceServiceImpl;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


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

    @PostMapping("/test-monthly-trigger")
    public ResponseEntity<String> testMonthlyAccrual() {
        LeaveBalanceServiceImpl leaveBalanceServiceImple = (LeaveBalanceServiceImpl) leaveBalanceService;
        leaveBalanceServiceImple.triggerMonthlyLeaveAccrual();
        return ResponseEntity.ok("Monthly leave accrual triggered successfully.");
    }
}

