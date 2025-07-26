package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
@CrossOrigin
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestServiceInterface leaveRequestService;
    private final EmployeeServiceInterface employeeService;
    private final LeaveTypeServiceInterface leaveTypeService;
    
    // ==================== EMPLOYEE OPERATIONS ====================
    
    /**
     * Apply for leave - Employee submits a new leave request
     */
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LeaveRequest>> applyLeave(@Valid @RequestBody LeaveRequestValidationDTO request) {
        try {
            // Validate the leave request
            ValidationResultDTO validationResult = leaveRequestService.validateLeaveRequest(request);
            if (!validationResult.isValid()) {
                String errorMessage = String.join("; ", validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, null));
            }
            
            // Save the leave request
            LeaveRequest savedLeaveRequest = leaveRequestService.saveLeaveRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave application submitted successfully", savedLeaveRequest));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error processing leave application: " + e.getMessage(), null));
        }
    }

    /**
     * Update leave request by employee
     */
    @PutMapping("/employee/update")
    public ResponseEntity<ApiResponse<ValidationResultDTO>> updateLeaveRequest(@RequestBody LeaveRequestUpdateDTO updateRequest) {
        try {
            // Get the employee and leave type entities
            Employee employee = employeeService.getByEmployeeId(updateRequest.getEmployeeId()).getBody();
            LeaveType leaveType = leaveTypeService.getLeaveTypeById(updateRequest.getLeaveTypeId()).getBody();
            
            if (employee == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Employee not found", null));
            }
            
            if (leaveType == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Leave type not found", null));
            }
            
            // Create LeaveRequest object with proper entities
            LeaveRequest leaveRequest = LeaveRequest.builder()
                    .leaveId(updateRequest.getLeaveId())
                    .employee(employee)
                    .leaveType(leaveType)
                    .startDate(updateRequest.getStartDate())
                    .endDate(updateRequest.getEndDate())
                    .daysRequested(updateRequest.getDaysRequested())
                    .reason(updateRequest.getReason())
                    .build();
            
            ValidationResultDTO result = leaveRequestService.updateRequest(leaveRequest);
            if (result.isValid()) {
                return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", result));
            } else {
                String errorMessage = String.join("; ", result.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, result));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error updating leave request: " + e.getMessage(), null));
        }
    }

    /**
     * Get all leave requests for an employee
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getEmployeeLeaveRequests(@PathVariable String employeeId) {
        try {
            List<LeaveRequest> leaveRequests = leaveRequestService.getLeaveRequestsByEmployee(employeeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    /**
     * Get a specific leave request by ID
     */
    @GetMapping("/{leaveId}")
    public ResponseEntity<ApiResponse<LeaveRequest>> getLeaveRequestById(@PathVariable String leaveId) {
        try {
            LeaveRequest leaveRequest = leaveRequestService.getLeaveRequestById(leaveId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request retrieved successfully", leaveRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "Leave request not found: " + e.getMessage(), null));
        }
    }

    /**
     * Cancel leave request by employee
     */
    @PutMapping("/{leaveId}/cancel")
    public ResponseEntity<ApiResponse<LeaveRequest>> cancelLeaveRequest(
            @PathVariable String leaveId,
            @RequestParam String employeeId) {
        try {
            LeaveRequest cancelledRequest = leaveRequestService.cancelLeaveRequest(leaveId, employeeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request cancelled successfully", cancelledRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Error cancelling request: " + e.getMessage(), null));
        }
    }

    // ==================== VALIDATION OPERATIONS ====================
    
    /**
     * Validate leave request without saving
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ValidationResultDTO>> validateLeaveRequest(@RequestBody LeaveRequestValidationDTO request) {
        try {
            ValidationResultDTO result = leaveRequestService.validateLeaveRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(true, "Validation completed", result));
        } catch (Exception e) {
            ValidationResultDTO errorResult = ValidationResultDTO.builder()
                    .isValid(false)
                    .build();
            errorResult.addError("Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Validation failed", errorResult));
        }
    }

    /**
     * Get employee leave balance
     */
    @GetMapping("/balance/{employeeId}/{leaveTypeId}")
    public ResponseEntity<ApiResponse<LeaveBalanceDTO>> getLeaveBalance(
            @PathVariable String employeeId,
            @PathVariable String leaveTypeId,
            @RequestParam(defaultValue = "2025") Integer year) {
        try {
            LeaveBalanceDTO balance = leaveRequestService.getEmployeeLeaveBalance(employeeId, leaveTypeId, year);
            if (balance != null) {
                return ResponseEntity.ok(new ApiResponse<>(true, "Balance retrieved successfully", balance));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(false, "Leave balance not found", null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving balance: " + e.getMessage(), null));
        }
    }

    /**
     * Check for overlapping leave requests
     */
    @PostMapping("/check-overlap")
    public ResponseEntity<ApiResponse<Boolean>> checkOverlappingRequests(@RequestBody LeaveRequestValidationDTO request) {
        try {
            List<LeaveRequest> overlappingRequests = leaveRequestService.getOverlappingRequests(
                    request.getEmployeeId(), request.getStartDate(), request.getEndDate());
            boolean hasOverlap = !overlappingRequests.isEmpty();
            String message = hasOverlap ? "Overlapping requests found" : "No overlapping requests";
            return ResponseEntity.ok(new ApiResponse<>(true, message, hasOverlap));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error checking overlaps: " + e.getMessage(), false));
        }
    }

    // ==================== MANAGER OPERATIONS ====================
    
    /**
     * Get pending leave requests for manager
     */
    @GetMapping("/manager/{managerId}/pending")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getPendingRequests(@PathVariable String managerId) {
        try {
            List<LeaveRequest> pendingRequests = leaveRequestService.getPendingRequestsForManager(managerId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Pending requests retrieved successfully", pendingRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving pending requests: " + e.getMessage(), null));
        }
    }

    /**
     * Get leave history for manager
     */
    @GetMapping("/manager/{managerId}/history")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getLeaveHistoryForManager(@PathVariable String managerId) {
        try {
            List<LeaveRequest> leaveHistory = leaveRequestService.getLeaveHistoryForManager(managerId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave history retrieved successfully", leaveHistory));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave history: " + e.getMessage(), null));
        }
    }

    /**
     * Approve leave request
     */
    @PutMapping("/{leaveId}/approve")
    public ResponseEntity<ApiResponse<LeaveRequest>> approveRequest(
            @PathVariable String leaveId, 
            @RequestParam String managerId) {
        try {
            LeaveRequest approvedRequest = leaveRequestService.approveRequest(leaveId, managerId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request approved successfully", approvedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error approving request: " + e.getMessage(), null));
        }
    }

    /**
     * Reject leave request
     */
    @PutMapping("/{leaveId}/reject")
    public ResponseEntity<ApiResponse<LeaveRequest>> rejectRequest(
            @PathVariable String leaveId,
            @RequestParam String managerId,
            @RequestParam String comment) {
        try {
            LeaveRequest rejectedRequest = leaveRequestService.rejectRequest(leaveId, managerId, comment);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request rejected successfully", rejectedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error rejecting request: " + e.getMessage(), null));
        }
    }

    /**
     * Update leave request by manager
     */
    @PutMapping("/manager/{leaveId}/update")
    public ResponseEntity<ApiResponse<LeaveRequest>> updateLeaveRequestByManager(
            @PathVariable String leaveId,
            @RequestParam String managerId,
            @RequestParam(required = false) String leaveTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            LeaveRequest updatedRequest = leaveRequestService.updateLeaveRequestByManager(
                    leaveId, managerId, leaveTypeId, startDate, endDate);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", updatedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error updating request: " + e.getMessage(), null));
        }
    }
}
