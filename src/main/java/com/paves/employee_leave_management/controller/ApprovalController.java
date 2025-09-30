package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApproveRequestDto;
import com.paves.employee_leave_management.dto.RejectRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.repository.EmployeeRepo;
import com.paves.employee_leave_management.service.ApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('HR')") // Or a more specific checker role
    public ResponseEntity<String> approveRequest(@PathVariable Long requestId, @RequestBody ApproveRequestDto dto) {
        // In a real app, get the checker from the security context.
        Employee checker = employeeRepo.findById("PAVEMP45179").orElseThrow(() -> new RuntimeException("Checker not found"));

        approvalService.approveRequest(requestId, dto, checker);
        return ResponseEntity.ok("Request approved successfully.");
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('HR')") // Or a more specific checker role
    public ResponseEntity<String> rejectRequest(@PathVariable Long requestId, @RequestBody RejectRequestDto dto) {
        // In a real app, get the checker from the security context.
        Employee checker = employeeRepo.findById("PAVEMP45179").orElseThrow(() -> new RuntimeException("Checker not found"));

        approvalService.rejectRequest(requestId, dto, checker);
        return ResponseEntity.ok("Request rejected successfully.");
    }
}
