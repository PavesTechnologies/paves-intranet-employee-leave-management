// 7. LeaveValidationController.java (in controller package)
package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.serviceInterface.LeaveValidationServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave/validation")
@CrossOrigin
public class LeaveValidationController {

    @Autowired
    private LeaveValidationServiceInterface leaveValidationService;

    @PostMapping("/validate")
    public ResponseEntity<ValidationResultDTO> validateLeaveRequest(@RequestBody LeaveRequestValidationDTO request) {
        try {
            ValidationResultDTO result = leaveValidationService.validateLeaveRequest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ValidationResultDTO errorResult = ValidationResultDTO.builder()
                    .isValid(false)
                    .build();
            errorResult.addError("Internal server error: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResult);
        }
    }

    @GetMapping("/balance/{employeeId}/{leaveTypeId}")
    public ResponseEntity<LeaveBalanceDTO> getLeaveBalance(
            @PathVariable String employeeId,
            @PathVariable String leaveTypeId,
            @RequestParam(defaultValue = "2025") Integer year) {
        try {
            LeaveBalanceDTO balance = leaveValidationService.getEmployeeLeaveBalance(employeeId, leaveTypeId, year);
            if (balance != null) {
                return ResponseEntity.ok(balance);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/check-overlap")
    public ResponseEntity<Boolean> checkOverlappingRequests(@RequestBody LeaveRequestValidationDTO request) {
        try {
            var overlappingRequests = leaveValidationService.getOverlappingRequests(
                    request.getEmployeeId(), request.getStartDate(), request.getEndDate());
            return ResponseEntity.ok(!overlappingRequests.isEmpty());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(false);
        }
    }
}