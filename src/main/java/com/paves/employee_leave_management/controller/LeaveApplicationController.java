package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.service.LeaveApplicationService;
import com.paves.employee_leave_management.serviceInterface.LeaveValidationServiceInterface;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/leave")
@CrossOrigin
public class LeaveApplicationController {

    @Autowired
    private LeaveApplicationService leaveApplicationService;

    @Autowired
    private LeaveValidationServiceInterface leaveValidationService;

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LeaveRequest>> applyLeave(@Valid @RequestBody LeaveRequestValidationDTO request) {
        try {
            // Fetch employee and leave type entities
            ValidationResultDTO validationResult = leaveValidationService.validateLeaveRequest(request);
            if (!validationResult.isValid()) {
                String errorMessage = String.join("; ", validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, null));
            }
            // Step 1: All validations passed, save the leave request
            LeaveRequest savedLeaveRequest = leaveApplicationService.saveLeaveRequest(request);

            return ResponseEntity.ok(new ApiResponse<>(true, "Leave application submitted successfully", savedLeaveRequest));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error processing leave application: " + e.getMessage(), null));
        }
    }
}
