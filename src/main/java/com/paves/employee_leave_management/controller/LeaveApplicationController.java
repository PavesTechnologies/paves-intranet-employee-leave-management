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

    @CrossOrigin
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LeaveRequest>> applyLeave(@Valid @RequestBody LeaveRequestValidationDTO request) {
        try {
            System.out.print("In Apply method before validation");

            System.out.print(request);
            // Step 1: Validate the leave request directly using service
            ValidationResultDTO validationResult = leaveValidationService.validateLeaveRequest(request);
            if (!validationResult.isValid()) {
                String errorMessage = String.join("; ", validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, errorMessage, null));
            }
            System.out.print("After Validation");



//            // Step 2: Check for overlapping leaves directly
//            Boolean hasOverlap = leaveValidationService.validateOverlaps(request);
//            if (hasOverlap) {
//                return ResponseEntity.badRequest()
//                        .body(new ApiResponse<>(false, "Leave request overlaps with existing approved leave", null));
//            }
//
//            // Step 3: Check leave balance directly
//            int currentYear = LocalDate.now().getYear();
//            LeaveBalanceDTO balanceResult = leaveValidationService.getLeaveBalance(request.getEmployeeId(), request.getLeaveTypeId(), currentYear);
//
//            if (balanceResult == null) {
//                return ResponseEntity.badRequest()
//                        .body(new ApiResponse<>(false, "Unable to retrieve leave balance", null));
//            }
//
//            if (balanceResult.getAvailableBalance() < request.getDaysRequested()) {
//                return ResponseEntity.badRequest()
//                        .body(new ApiResponse<>(false, "Insufficient leave balance. Available: " +
//                                balanceResult.getAvailableBalance() + ", Requested: " + request.getDaysRequested(), null));
//            }

            // Step 4: All validations passed, save the leave request
            LeaveRequest savedLeaveRequest = leaveApplicationService.saveLeaveRequest(request);

            return ResponseEntity.ok(new ApiResponse<>(true, "Leave application submitted successfully", savedLeaveRequest));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error processing leave application: " + e.getMessage(), null));
        }
    }
}
