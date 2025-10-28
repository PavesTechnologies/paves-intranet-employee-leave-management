package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveTypeIdDTO;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.entities.LeaveTypesEnum;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.security.CurrentUser;
import com.paves.employee_leave_management.service.HrOperationService;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    private LeaveTypeServiceInterface service;

    @Autowired
    private LeaveTypeRepo leaveTypeRepo;

//    @Autowired
//    private EmailServiceInterface.ApprovalService approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private HrOperationService hrOperationService;

//    // This is a placeholder for getting the user from the JWT token
//    private Employee getAuthenticatedUser() {
//        // In a real application, you would extract the user details from the Spring Security Context.
//        // For now, we'll fetch a hardcoded user to simulate this.
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//
//        if (authentication == null || !authentication.isAuthenticated()) {
//            throw new RuntimeException("No authenticated user found");
//        }
//
//        Object principal = authentication.getPrincipal();
//
//        if (principal instanceof Jwt jwt) {
//            // You can fetch using email or user_id depending on your DB
//            //String email = jwt.getClaim("email");  // "employee1@example.com"
//             Long userId = jwt.getClaim("user_id"); // If needed
//
//            return employeeRepo.findByEmployeeId(String.valueOf(userId))
//                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
//        }
//
//        throw new RuntimeException("Invalid authentication principal");
//    }

    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    public List<Map<String, String>> getLeaveTypes() {
        return Arrays.stream(LeaveTypesEnum.values())
                .map(type -> Map.of(
                        "name", type.name(),
                        "label", type.getLabel()
                ))
                .toList();
    }

//    @PostMapping("/add-leave-type")
//    @PreAuthorize("hasRole('HR')")
//    public ResponseEntity<ApiResponse<Object>> addLeaveType(@RequestBody LeaveType leaveType) {
//        Employee maker = getAuthenticatedUser();
//        // Assuming the role is stored in the jobTitle field for now
//        // String makerRole = maker.getJobTitle();
//        String makerRole = "HR";
//
//        MCApprovalRequestDto dto = new MCApprovalRequestDto();
//        dto.setActionType(ActionType.CREATE_LEAVE_TYPE);
//
//        Map<String, Object> payload = new HashMap<>();
//        payload.put("newData", leaveType);
//        dto.setPayload(payload);
//
//        approvalService.submitForApproval(dto, maker, makerRole);
//
//        return ResponseEntity.ok(new ApiResponse<>(true,"Request to add leave type has been submitted for approval.",null));
//    }

    @PostMapping("/add-leave-type")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> addLeaveType(
            @RequestBody LeaveType leaveTypeData,
            @CurrentUser Employee maker) { // Inject maker directly

        try {
            // Call the NEW HrOperationService to submit
            hrOperationService.submitNewLeaveType(maker, leaveTypeData);

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to add leave type [" + leaveTypeData.getLeaveName() + "] has been submitted for approval.",
                    null
            ));
        } catch (Exception e) {
            // Log the exception properly
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting request: " + e.getMessage(), null));
        }
    }

    @GetMapping("/get-all-leave-types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        return service.getAllLeaveTypes();
    }

    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> updateLeaveType(
            @PathVariable String leaveTypeId,
            @RequestBody LeaveType updatedLeaveTypeData, // Represents the desired new state
            @CurrentUser Employee maker) {

        try {
            // It's good practice to fetch the old state for audit/comparison if needed by the workflow payload
            LeaveType oldLeaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                    .orElseThrow(() -> new RuntimeException("LeaveType not found with ID: " + leaveTypeId));

            // Structure the payload for the HrOperationRequest.
            // You might want a DTO or Map. Here using Map for flexibility.
            Map<String, Object> updatePayload = Map.of(
                    "leaveTypeId", leaveTypeId, // Include ID for context
                    "before", oldLeaveType, // Optional: for audit trail or display in approval
                    "after", updatedLeaveTypeData // The new data
            );

            // Call the NEW HrOperationService
            // You need to add `submitUpdateLeaveType` method to HrOperationService
            hrOperationService.submitUpdateLeaveType(maker, updatePayload);

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to update leave type [" + leaveTypeId + "] has been submitted for approval.",
                    null
            ));
        } catch (RuntimeException e) { // Catch specific exceptions like NotFound
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            // Log the exception
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting update request: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/delete-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> deactivateLeaveType(
            @PathVariable String leaveTypeId,
            @CurrentUser Employee maker) {

        try {
            // Create a simple payload just containing the ID
            Map<String, Object> deactivatePayload = Map.of("leaveTypeId", leaveTypeId);

            // Call the NEW HrOperationService
            hrOperationService.submitDeactivateLeaveType(maker, deactivatePayload);

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to deactivate leave type [" + leaveTypeId + "] has been submitted for approval.",
                    null
            ));
        } catch (Exception e) {
            // Log the exception
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting deactivation request: " + e.getMessage(), null));
        }
    }

    // Document management endpoints remain unchanged


//    @PreAuthorize("hasRole('HR')")
//    @PostMapping("/{leaveTypeId}/upload-document")
//    public ResponseEntity<String> uploadDocument(@PathVariable String leaveTypeId,
//                                                 @RequestParam("file") MultipartFile file) throws Exception {
//        service.uploadDocument(leaveTypeId, file);
//        return ResponseEntity.ok("Document uploaded successfully");
//    }
//
//    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
//    @GetMapping("/{leaveTypeName}/document")
//    public ResponseEntity<ByteArrayResource> viewDocument(@PathVariable String leaveTypeName,
//                                                          @RequestParam(defaultValue = "pdf") String fileType) throws Exception {
//        byte[] data = service.viewDocument(leaveTypeName, fileType);
//
//        ByteArrayResource resource = new ByteArrayResource(data);
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"policy." + fileType + "\"")
//                .contentType(MediaType.parseMediaType(service.getMimeType(fileType)))
//                .contentLength(data.length)
//                .body(resource);
//    }
//
//    @PreAuthorize("hasRole('HR')")
//    @DeleteMapping("/{leaveTypeId}/document")
//    public ResponseEntity<ApiResponse<Object>> deleteDocument(@PathVariable String leaveTypeId) throws Exception {
//        service.deleteDocument(leaveTypeId);
//        return ResponseEntity.ok(new ApiResponse<>(true,"Document deleted successfully",null));
//    }

    @GetMapping("/get-all-leave-type-ids")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveTypeIdDTO>> getAllLeaveTypeIds() {
        return new ResponseEntity<>(service.getAllLeaveTypeIds(), HttpStatus.OK);
    }

}
