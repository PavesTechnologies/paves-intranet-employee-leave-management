package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/leave-requests")
@CrossOrigin
@RequiredArgsConstructor
public class LeaveRequestController {
    private final LeaveRequestServiceInterface leaveRequestService;
    private final EmployeeServiceInterface employeeService;
    private final LeaveTypeServiceInterface leaveTypeService;
    private final LeaveRequestRepo leaveRequestRepo;
    private final SimpMessagingTemplate template;
    private final GenderBasedLeaveServiceInterface genderBasedLeaveServiceInterface;

    // ==================== EMPLOYEE OPERATIONS ====================

    /**
     * Apply for leave - Employee submits a new leave request
     */
    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('GENERAL','HR', 'MANAGER') and hasAuthority('EDIT_TIMESHEET') and @permissionService.isOwner(authentication, #request.employeeId)")
    public ResponseEntity<ApiResponse<LeaveRequest>> applyLeave(@Valid @RequestBody LeaveRequestValidationDTO request) {
        try {
            // Validate the leave request
          /*  ValidationResultDTO validationResult = leaveRequestService.validateLeaveRequest(request);
            if (!validationResult.isValid()) {
                String errorMessage = String.join("; ", validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, null));
            }*/

            // Save the leave request
            LeaveRequest savedLeaveRequest = leaveRequestService.saveLeaveRequest(request);
            template.convertAndSend("/topic/data-updated", "updated");
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave application submitted successfully", savedLeaveRequest));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * Update leave request by employee
     */
    @PutMapping("/employee/update")
    @PreAuthorize("@permissionService.isOwnerOfLeaveRequest(authentication, #validationDTO.leaveId)")
    public ResponseEntity<ApiResponse<ValidationResultDTO>> updateLeaveRequest(@RequestBody LeaveRequestValidationDTO validationDTO) {

        // Get the employee and leave type entities
        Employee employee = employeeService.getByEmployeeId(validationDTO.getEmployeeId()).getBody();
        if(validationDTO.getLeaveTypeId().equals("L-ML") || validationDTO.getLeaveTypeId().equals("L-PL")){
            if (employee == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Employee not found", null));
            }
            Optional<GenderBasedLeave> genderBasedLeaveOpt = genderBasedLeaveServiceInterface.getLeaveType(validationDTO.getLeaveTypeId());
            if(genderBasedLeaveOpt.isEmpty()){
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Gender-based leave type not found", null));
            }

            LeaveRequest leaveRequest = LeaveRequest.builder()
                    .leaveId(validationDTO.getLeaveId())
                    .employee(employee)
                    .genderBasedLeaveType(genderBasedLeaveOpt.get())
                    .startDate(validationDTO.getStartDate())
                    .endDate(validationDTO.getEndDate())
                    .daysRequested(validationDTO.getDaysRequested())
                    .startSession(validationDTO.getStartSession())
                    .endSession(validationDTO.getEndSession())
                    .reason(validationDTO.getReason())
                    .requestDate(validationDTO.getRequestDate())
                    .year(validationDTO.getYear())
                    .build();

            ValidationResultDTO result = leaveRequestService.updateRequestByEmployee(leaveRequest, validationDTO);
            if (result.isValid()) {
                template.convertAndSend("/topic/data-updated", "updated");
                return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", result));
            } else {
                String errorMessage = String.join("; ", result.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, result));
            }

        }else{
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
                    .requestDate(validationDTO.getRequestDate())
                    .year(validationDTO.getYear())
                    .build();

            ValidationResultDTO result = leaveRequestService.updateRequestByEmployee(leaveRequest, validationDTO);
            if (result.isValid()) {
                template.convertAndSend("/topic/data-updated", "updated");
                return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", result));
            } else {
                String errorMessage = String.join("; ", result.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, result));
            }
        }
    }

    /**
     * Get all leave requests for an employee
     * without year
     */
    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponseDTO>>> getEmployeeLeaveRequests(@PathVariable String employeeId) {
        try {
            List<LeaveRequestResponseDTO> leaveRequests = leaveRequestService.getLeaveRequestsByEmployee(employeeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }


    @GetMapping("/employee/{employeeId}/{year}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponseDTO>>> getEmployeeLeaveRequestsOfEmployeeByYear(@PathVariable String employeeId, @PathVariable int year) {
        try {
            List<LeaveRequestResponseDTO> leaveRequests = leaveRequestService.getLeaveRequestsByEmployeeAndByYear(employeeId, year);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    // without year
    @GetMapping("/employee/pending/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponseDTO>>> getEmployeePendingLeaveRequests(@PathVariable String employeeId) {
        try {
            List<LeaveRequestResponseDTO> leaveRequests = leaveRequestService.getPendingLeaveRequestsByEmployee(employeeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    // withYear
    @GetMapping("/employee/pending/{employeeId}/{year}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponseDTO>>> getEmployeePendingLeaveRequestsAndYear(@PathVariable String employeeId, @PathVariable int year) {
        try {
            List<LeaveRequestResponseDTO> leaveRequests = leaveRequestService.getPendingLeaveRequestsByEmployeeAndYear(employeeId, year);
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
    @PreAuthorize("@permissionService.isOwnerOfLeaveRequest(authentication, #leaveId) or @permissionService.isManagerOfLeaveRequest(authentication, #leaveId) or hasRole('HR')")
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
    @PreAuthorize("@permissionService.isOwnerOfLeaveRequest(authentication, #leaveId) and @permissionService.isOwner(authentication, #employeeId)")
    public ResponseEntity<ApiResponse<LeaveRequest>> cancelLeaveRequest(
            @PathVariable String leaveId,
            @PathVariable String employeeId) {
        try {
            LeaveRequest cancelledRequest = leaveRequestService.cancelLeaveRequest(leaveId, employeeId);
            template.convertAndSend("/topic/data-updated", "updated");
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
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
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
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isOwner(authentication, #queryDTO.managerId)")
    public ResponseEntity<ApiResponse<List<LeaveRequestManagerViewDTO>>> getRequestsForManager(@Valid @RequestBody ManagerQueryDTO queryDTO) {
        try {
            List<LeaveRequestManagerViewDTO> requests = leaveRequestService.getRequestsForManager(queryDTO);
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
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isOwner(authentication, #queryDTO.managerId)")
    public ResponseEntity<ApiResponse<List<LeaveRequestManagerViewDTO>>> getLeaveHistoryForManager(@Valid @RequestBody ManagerQueryDTO queryDTO) {
        try {

//            List<LeaveRequest> leaveHistory = leaveRequestService.getLeaveHistoryForManager(queryDTO);
            List<LeaveRequestManagerViewDTO> leaveHistory = leaveRequestService.getRequestsForManager(queryDTO);
            template.convertAndSend("/topic/data-updated", "updated");
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave history retrieved successfully", leaveHistory));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave history: " + e.getMessage(), null));
        }
    }


    @GetMapping("/manager/pending-count/{managerId}")
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isOwner(authentication, #managerId)")
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
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isManagerOfLeaveRequest(authentication, #approvalRequest.leaveId)")
    public ResponseEntity<ApiResponse<LeaveRequest>> approveRequest(@Valid @RequestBody ApprovalRequestDTO approvalRequest) {
        try {
            LeaveRequest approvedRequest = leaveRequestService.approveRequest(approvalRequest);
            template.convertAndSend("/topic/data-updated", "updated");
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
        template.convertAndSend("/topic/data-updated", "updated");
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/reject-batch")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<List<LeaveRequest>> rejectLeaveBatch(
            @Valid @RequestBody BatchApprovalRequestDTO batchApproval) {
        List<LeaveRequest> rejected = leaveRequestService.rejectMultipleRequests(batchApproval);
        template.convertAndSend("/topic/data-updated", "updated");
        return ResponseEntity.ok(rejected);
    }


    /**
     * Reject leave request using request body
     */
    @PutMapping("/reject")
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isManagerOfLeaveRequest(authentication, #rejectionRequest.leaveId)")
    public ResponseEntity<ApiResponse<LeaveRequest>> rejectRequest(@Valid @RequestBody RejectionRequestDTO rejectionRequest) {
        try {
            LeaveRequest rejectedRequest = leaveRequestService.rejectRequest(rejectionRequest);
            template.convertAndSend("/topic/data-updated", "updated");
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
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isManagerOfLeaveRequest(authentication, #updateRequest.leaveId)")
    public ResponseEntity<ApiResponse<LeaveRequest>> updateLeaveRequestByManager(@Valid @RequestBody ManagerUpdateRequestDTO updateRequest) {
        try {
            LeaveRequest updatedRequest = leaveRequestService.updateLeaveRequestByManager(updateRequest);
            template.convertAndSend("/topic/data-updated", "updated");
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request updated successfully", updatedRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/history/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<List<LeaveRequest>> getLeaveHistoryByYear(
            @PathVariable String employeeId,
            @RequestParam int year
    ) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        return ResponseEntity.ok(leaveRequestService.getLeaveHistoryByYear(employeeId, startDate, endDate));
    }

    @GetMapping("employee/pendingAndApproved-leave/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
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
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isManagerOfLeaveRequest(authentication, #rejectionRequest.leaveId)")
    public ResponseEntity<ApiResponse<LeaveRequest>> cancelLeaveRequestByManager(@RequestBody RejectionRequestDTO rejectionRequest) {
        try {
            LeaveRequest cancelledRequest = leaveRequestService.rejectRequest(rejectionRequest);
            template.convertAndSend("/topic/data-updated", "updated");
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave request cancelled successfully", cancelledRequest));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error cancelling request: " + e.getMessage(), null));
        }
    }

    @GetMapping("/view-details")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> leaveBalanceViewDetails(@RequestParam String employeeId, @RequestParam String leaveName, @RequestParam int year) {
        try {
            List<LeaveRequest> leaveRequests = leaveRequestService.leaveBalanceViewDetails(employeeId, leaveName, year);
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", leaveRequests));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving leave requests: " + e.getMessage(), null));
        }
    }

    @GetMapping("/getAllLeaves/{year}/{month}")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER')")
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

    @GetMapping("/getLeaveRequests/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasAnyRole('GENERAL','MANAGER','HR')")
    public ResponseEntity<?> getActiveLeavesForEmployee(
            @PathVariable("employeeId") String employeeId,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
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

    @GetMapping("/approved/{year}")
    @PreAuthorize("hasRole('RESOURCE-MANAGER')")
    public ResponseEntity<ApiResponse<List<EmployeeApprovedLeavesDTO>>> getAllApprovedLeavesByYear(
            @PathVariable Integer year) {
        try {
            List<EmployeeApprovedLeavesDTO> approvedLeaves = leaveRequestService.getAllApprovedLeavesByYearGroupedByEmployee(year);
            return ResponseEntity.ok(new ApiResponse<>(true, "All approved leaves retrieved successfully", approvedLeaves));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving approved leaves: " + e.getMessage(), null));
        }
    }

    @GetMapping("/approved/{employeeId}/{year}")
    @PreAuthorize("hasRole('RESOURCE-MANAGER')")
    public ResponseEntity<ApiResponse<EmployeeApprovedLeavesDTO>> getApprovedLeavesByEmployeeAndYear(
            @PathVariable String employeeId, 
            @PathVariable Integer year) {
        try {
            EmployeeApprovedLeavesDTO approvedLeaves = leaveRequestService.getApprovedLeavesByYearForEmployee(employeeId, year);
            if (approvedLeaves != null) {
                return ResponseEntity.ok(new ApiResponse<>(true, "Approved leaves retrieved successfully", approvedLeaves));
            } else {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(true, "No approved leaves found for employee in " + year, approvedLeaves));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving approved leaves: " + e.getMessage(), null));
        }
    }

    @PostMapping("/apply-on-behalf")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> applyLeaveBehalf(@RequestBody LeaveRequestValidationDTO leaveRequestValidationDTO){
        try {
            // Validate the leave request
            ValidationResultDTO validationResult = leaveRequestService.validateLeaveRequest(leaveRequestValidationDTO);
            if (!validationResult.isValid()) {
                String errorMessage = String.join("; ", validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, null));
            }

            // Save the leave request
            LeaveRequest savedLeaveRequest = leaveRequestService.saveLeaveRequest(leaveRequestValidationDTO);
            template.convertAndSend("/topic/data-updated", "updated");
            return ResponseEntity.ok(new ApiResponse<>(true, "Leave application submitted successfully", savedLeaveRequest));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error processing leave application: " + e.getMessage(), null));
        }
    }
}
