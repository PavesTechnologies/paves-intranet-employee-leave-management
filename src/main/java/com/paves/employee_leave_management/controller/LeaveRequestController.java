package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final LeaveRequestRepo leaveRequestRepo;

    // ==================== EMPLOYEE OPERATIONS ====================

    /**
     * Apply for leave - Employee submits a new leave request
     */
    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('GENERAL','HR', 'MANAGER') and hasAuthority('EDIT_TIMESHEET')")
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
    @PreAuthorize("hasAnyRole('GENERAL', 'MANAGER', 'HR')")
    public ResponseEntity<ApiResponse<ValidationResultDTO>> updateLeaveRequest(@RequestBody LeaveRequestValidationDTO validationDTO) {

        // Get the employee and leave type entities
        Employee employee = employeeService.getByEmployeeId(validationDTO.getEmployeeId()).getBody();
        LeaveType leaveType = leaveTypeService.getLeaveTypeById(validationDTO.getLeaveTypeId()).getBody();

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
                .leaveId(validationDTO.getLeaveId())
                .employee(employee)
                .leaveType(leaveType)
                .startDate(validationDTO.getStartDate())
                .endDate(validationDTO.getEndDate())
                .daysRequested(validationDTO.getDaysRequested())
                .startSession(validationDTO.getStartSession())
                .endSession(validationDTO.getEndSession())
                .reason(validationDTO.getReason())
                .requestDate(LocalDate.now())
                .build();

        ValidationResultDTO result = leaveRequestService.updateRequestByEmployee(leaveRequest, validationDTO);
        if (result.isValid()) {
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", result));
        } else {
            String errorMessage = String.join("; ", result.getErrors());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, errorMessage, result));
        }
    }

    /**
     * Get all leave requests for an employee
     */
    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'GENERAL')")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getEmployeeLeaveRequests(@PathVariable String employeeId) {
        try {
            List<LeaveRequest> leaveRequests = leaveRequestService.getLeaveRequestsByEmployee(employeeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    @GetMapping("/employee/pending/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'GENERAL')")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getEmployeePendingLeaveRequests(@PathVariable String employeeId) {
        try {
            List<LeaveRequest> leaveRequests = leaveRequestService.getPendingLeaveRequestsByEmployee(employeeId);
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
    @PreAuthorize("hasAnyRole('GENERAL','MANAGER','HR')")
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
    @PutMapping("/{leaveId}/cancel/{employeeId}")
    @PreAuthorize("hasAnyRole('GENERAL', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<LeaveRequest>> cancelLeaveRequest(
            @PathVariable String leaveId,
            @PathVariable String employeeId) {
        try {
            LeaveRequest cancelledRequest = leaveRequestService.cancelLeaveRequest(leaveId, employeeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Cancelled By employee", cancelledRequest));
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
    @PreAuthorize("hasAnyRole('GENERAL','MANAGER','HR')")
    public ResponseEntity<ApiResponse<LeaveBalanceDTO>> getLeaveBalance(
            @PathVariable String employeeId,
            @PathVariable String leaveTypeId,
            @RequestParam(required = false) Integer year) {
        try {
            if (year == null) {
                year = LocalDate.now().getYear();
            }
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
     * Get pending/filtered leave requests for manager
     */
    @PostMapping("/manager/requests")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getRequestsForManager(@Valid @RequestBody ManagerQueryDTO queryDTO) {
        try {
            List<LeaveRequest> requests = leaveRequestService.getRequestsForManager(queryDTO);
            return ResponseEntity.ok(new ApiResponse<>(true, "Requests retrieved successfully", requests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving requests: " + e.getMessage(), null));
        }
    }

    /**
     * Get leave history for manager with filtering
     */
    @PostMapping("/manager/history")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getLeaveHistoryForManager(@Valid @RequestBody ManagerQueryDTO queryDTO) {
        try {
            List<LeaveRequest> leaveHistory = leaveRequestService.getLeaveHistoryForManager(queryDTO);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave history retrieved successfully", leaveHistory));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave history: " + e.getMessage(), null));
        }
    }


    @GetMapping("/manager/pending-count/{managerId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<Long>> getPendingCountForManager(@PathVariable String managerId) {
        try {
            Long count = leaveRequestRepo.countPendingLeavesByManager(managerId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Pending count retrieved successfully", count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving pending count: " + e.getMessage(), null));
        }
    }

    /**
     * Approve leave request using request body
     */
    @PutMapping("/approve")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<LeaveRequest>> approveRequest(@Valid @RequestBody ApprovalRequestDTO approvalRequest) {
        try {
            LeaveRequest approvedRequest = leaveRequestService.approveRequest(approvalRequest);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request approved successfully", approvedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error approving request: " + e.getMessage(), null));
        }
    }

    /**
     * Approve leave request using request body
     */

    @PostMapping("/approve-batch")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<List<LeaveRequest>> approveLeaveBatch(
            @Valid @RequestBody BatchApprovalRequestDTO batchApproval) {

        List<LeaveRequest> approved = leaveRequestService.approveMultipleRequests(batchApproval);
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/reject-batch")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<List<LeaveRequest>> rejectLeaveBatch(
            @Valid @RequestBody BatchApprovalRequestDTO batchApproval) {

        List<LeaveRequest> rejected = leaveRequestService.rejectMultipleRequests(batchApproval);
        return ResponseEntity.ok(rejected);
    }


    /**
     * Reject leave request using request body
     */
    @PutMapping("/reject")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<LeaveRequest>> rejectRequest(@Valid @RequestBody RejectionRequestDTO rejectionRequest) {
        try {
            LeaveRequest rejectedRequest = leaveRequestService.rejectRequest(rejectionRequest);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request rejected successfully", rejectedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error rejecting request: " + e.getMessage(), null));
        }
    }

    /**
     * Update leave request by manager using request body
     */
    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<LeaveRequest>> updateLeaveRequestByManager(@Valid @RequestBody ManagerUpdateRequestDTO updateRequest) {
        try {
            LeaveRequest updatedRequest = leaveRequestService.updateLeaveRequestByManager(updateRequest);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", updatedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error updating request: " + e.getMessage(), null));
        }
    }

    @GetMapping("/history/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<List<LeaveRequest>> getLeaveHistoryByYear(
            @PathVariable String employeeId,
            @RequestParam int year
    ) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        return ResponseEntity.ok(leaveRequestService.getLeaveHistoryByYear(employeeId, startDate, endDate));
    }

    @GetMapping("employee/pendingAndApproved-leave/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<PendingAndApprovedLeaveRequestsDTO>>> getPendingLeaveAndApprovedLeaveByEmployeeId(@PathVariable String employeeId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        try {
            List<PendingAndApprovedLeaveRequestsDTO> leaveRequests = leaveRequestService.getPendingLeaveAndApprovedLeaveByEmployeeId(employeeId, startDate, endDate);
            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Leave requests retrieved successfully",
                    leaveRequests
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    @PutMapping("/cancel")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<LeaveRequest>> cancelLeaveRequestByManager(@RequestBody RejectionRequestDTO rejectionRequest) {
        try {
            LeaveRequest cancelledRequest = leaveRequestService.rejectRequest(rejectionRequest);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request cancelled successfully", cancelledRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error cancelling request: " + e.getMessage(), null));
        }
    }

    @GetMapping("/view-details")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> leaveBalanceViewDetails(@RequestParam String employeeId, @RequestParam String leaveName, @RequestParam int year) {
        try {
            List<LeaveRequest> leaveRequests = leaveRequestService.leaveBalanceViewDetails(employeeId, leaveName, year);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    @GetMapping("/getLeaveRequests/{employeeId}/{year}/{month}")
    public ResponseEntity<?> getActiveLeavesForEmployee(
            @PathVariable("employeeId") String employeeId,
            @PathVariable(value = "year", required = false) Integer year,
            @PathVariable(value = "month", required = false) Integer month) {
        try {
            List<LeaveRequestDTO> leaves = leaveRequestService.getAllLeaveRequestsExceptCancelled(employeeId, month, year);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaves));
        } catch (LeaveBalanceExceptionHandler ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, ex.getMessage(), null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "An error occurred while fetching leave requests", null));
        }
    }
    
    @GetMapping("/getAllLeaves/{year}/{month}")
    public ResponseEntity<?> getAllLeavesForMonthYear(
            @PathVariable(value = "year", required = false) Integer year,
            @PathVariable(value = "month", required = false) Integer month) {
        try {
            List<LeaveRequestDTO> leaves = leaveRequestService.getAllEmployeesLeaveRequestsByMonthYear(month, year);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaves));
        } catch (LeaveBalanceExceptionHandler ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, ex.getMessage(), null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "An error occurred while fetching leave requests", null));
        }
    }
}
