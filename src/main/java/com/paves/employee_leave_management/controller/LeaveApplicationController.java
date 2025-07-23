package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.service.LeaveApplicationService;
import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.dto.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.Valid;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/leave")
@CrossOrigin(origins = "*")
public class LeaveApplicationController {

    @Autowired
    private LeaveApplicationService leaveApplicationService;

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LeaveRequest>> applyLeave(@Valid @RequestBody LeaveRequestValidationDTO request) {
        try {
            // Step 1: Validate the leave request using existing validation endpoint
            ValidationResultDTO validationResult = validateLeaveRequest(request);
            if (!validationResult.isValid()) {
                String errorMessage = String.join("; ", validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, null));
            }

            // Step 2: Check for overlapping leaves using existing endpoint
            Boolean hasOverlap = checkOverlap(request);
            if (hasOverlap) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Leave request overlaps with existing approved leave", null));
            }

            // Step 3: Check leave balance using existing endpoint
            LeaveBalanceDTO balanceResult = checkBalance(request.getEmployeeId(), request.getLeaveTypeId());
            if (balanceResult == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Unable to retrieve leave balance", null));
            }

            if (balanceResult.getAvailableBalance() < request.getDaysRequested()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Insufficient leave balance. Available: " +
                                balanceResult.getAvailableBalance() + ", Requested: " + request.getDaysRequested(), null));
            }

            // Step 4: All validations passed, save the leave request
            LeaveRequest savedLeaveRequest = leaveApplicationService.saveLeaveRequest(request);

            return ResponseEntity.ok(new ApiResponse<>(true, "Leave application submitted successfully", savedLeaveRequest));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error processing leave application: " + e.getMessage(), null));
        }
    }

    private ValidationResultDTO validateLeaveRequest(LeaveRequestValidationDTO request) {
        try {
            return restTemplate.postForObject("/api/leave/validation/validate", request, ValidationResultDTO.class);
        } catch (Exception e) {
            ValidationResultDTO errorResult = new ValidationResultDTO();
            errorResult.addError("Validation service error: " + e.getMessage());
            return errorResult;
        }
    }

    private Boolean checkOverlap(LeaveRequestValidationDTO request) {
        try {
            return restTemplate.postForObject("/api/leave/validation/check-overlap", request, Boolean.class);
        } catch (Exception e) {
            return true; // Assume overlap exists on error for safety
        }
    }

    private LeaveBalanceDTO checkBalance(String employeeId, String leaveTypeId) {
        try {
            int currentYear = LocalDate.now().getYear();
            return restTemplate.getForObject("/api/leave/validation/balance/" + employeeId + "/" + leaveTypeId + "?year=" + currentYear, LeaveBalanceDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}