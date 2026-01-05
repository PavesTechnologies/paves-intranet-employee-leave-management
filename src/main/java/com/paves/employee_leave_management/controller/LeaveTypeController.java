package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveTypeIdDTO;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.dto.UpdateLeaveRequest;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.AccrualFrequency;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    private LeaveTypeServiceInterface service;

    @Autowired
    private LeaveTypeRepo leaveTypeRepo;

    @Autowired
    private ApprovalServiceInterface approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private GenderBasedRepo genderBasedRepo;

    // This is a placeholder for getting the user from the JWT token
    private Employee getAuthenticatedUser() {
        // In a real application, you would extract the user details from the Spring Security Context.
        // For now, we'll fetch a hardcoded user to simulate this.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            // You can fetch using email or user_id depending on your DB
            //String email = jwt.getClaim("email");  // "employee1@example.com"
            Long userId = jwt.getClaim("user_id"); // If needed

            return employeeRepo.findByEmployeeId(String.valueOf(userId))
                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
        }

        throw new RuntimeException("Invalid authentication principal");
    }

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

    @GetMapping("/accrual-frequencies")
    @PreAuthorize("hasAnyRole('HR')")
    public List<String> getAccrualFrequencies() {
        return Arrays.stream(AccrualFrequency.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }


    @PostMapping("/add-leave-type")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> addLeaveType(@Valid @RequestBody LeaveType leaveType) {
        Employee maker = getAuthenticatedUser();
        // Assuming the role is stored in the jobTitle field for now
        // String makerRole = maker.getJobTitle();

        String makerRole = "HR";
        if (leaveType.getEffectiveStartDate() == null) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(
                            false,
                            "Leave type effective start date is required",
                            null
                    ));
        }

//        Optional<LeaveType> existingLeaveType = leaveTypeRepo.findByLeaveNameIgnoreCase(leaveType.getLeaveName());
//        if (existingLeaveType.isPresent()) {
//            return ResponseEntity
//                    .status(HttpStatus.CONFLICT)
//                    .body(new ApiResponse<>(
//                            false,
//                            "Leave type '" + leaveType.getLeaveName() + "' already exists.",
//                            null
//                    ));
//        }
        Optional<LeaveType> existingLeaveType = leaveTypeRepo.findByLeaveNameIgnoreCase(leaveType.getLeaveName());


        if (existingLeaveType.isPresent()) {
            LeaveType existing = existingLeaveType.get();

            if (Boolean.TRUE.equals(existing.getActive())) {
                // If the leave type exists and is active → block creation
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponse<>(
                                false,
                                "Leave type '" + leaveType.getLeaveName() + "' already exists and is active.",
                                null
                        ));
            }
        }

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.CREATE_LEAVE_TYPE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("newData", leaveType);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker, makerRole);

        return ResponseEntity.ok(new ApiResponse<>(true, "Request to add leave type has been submitted for approval.", null));
    }

    @GetMapping("/get-all-leave-types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        return service.getAllLeaveTypes();
    }

    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> updateLeave(@PathVariable String leaveTypeId, @RequestBody UpdateLeaveRequest request) {
        Employee maker = getAuthenticatedUser();
//        String makerRole = maker.getJobTitle();
        String makerRole = "HR";

        LeaveType leaveType = null;
        GenderBasedLeave genderBasedLeave = null ;
        if ("REGULAR".equalsIgnoreCase(request.getUpdateType())) {
            leaveType = request.getLeaveType();
            // handle regular leave update
        }

        if ("GENDER_BASED".equalsIgnoreCase(request.getUpdateType())) {
            genderBasedLeave = request.getGenderBasedLeave();
            // handle gender based leave update
        }

        if(genderBasedLeave != null){
            if (
                    LeaveTypesEnum.MATERNITY_LEAVE.name().equalsIgnoreCase(genderBasedLeave.getLeaveName()) ||
                            LeaveTypesEnum.PATERNITY_LEAVE.name().equalsIgnoreCase(genderBasedLeave.getLeaveName())
            ){
                GenderBasedLeave toUpdate = genderBasedRepo.findByLeaveNameIgnoreCase(genderBasedLeave.getLeaveName()).orElseThrow(() -> new RuntimeException("GenderBasedLeave not found"));
                MCApprovalRequestDto dto = new MCApprovalRequestDto();
                dto.setActionType(ActionType.UPDATE_GENDER_BASED_LEAVE);
                dto.setEntityId(leaveTypeId);

                Map<String, Object> payload = new HashMap<>();
                payload.put("before", toUpdate);
                payload.put("after", genderBasedLeave);
                dto.setPayload(payload);

                approvalService.submitForApproval(dto, maker, makerRole);

                return ResponseEntity.ok(new ApiResponse<>(true, "Request to update leave type has been submitted for approval.", null));
            }
        }

        LeaveType oldLeaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId).orElseThrow(() -> new RuntimeException("LeaveType not found"));

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.UPDATE_LEAVE_TYPE);
        dto.setEntityId(leaveTypeId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("before", oldLeaveType);
        payload.put("after", leaveType);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker, makerRole);

        return ResponseEntity.ok(new ApiResponse<>(true, "Request to update leave type has been submitted for approval.", null));
    }

    @DeleteMapping("/delete-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> deleteLeaveType(
            @PathVariable String leaveTypeId,
            @RequestBody Map<String, String> requestBody) {

        Employee maker = getAuthenticatedUser();
        String makerRole = "HR";

        String effectiveDateStr = requestBody.get("deactivationEffectiveDate");


        // Optional: validate input
        if (effectiveDateStr == null || effectiveDateStr.isEmpty()) {
            throw new IllegalArgumentException("Deactivation effective date is required.");
        }

        Optional<GenderBasedLeave> genderBasedLeave = genderBasedRepo.findById(leaveTypeId);
        Optional<LeaveType> leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);
        if(genderBasedLeave.isEmpty() && leaveType.isEmpty()){
            return ResponseEntity.ok(new ApiResponse<>(false, "Leave type not found", null));
        }

        if(genderBasedLeave.get().getLeaveTypeId() == leaveTypeId){
            MCApprovalRequestDto dto = new MCApprovalRequestDto();
            dto.setActionType(ActionType.DEACTIVATE_GENDER_BASED_LEAVE_TYPE);
            dto.setEntityId(leaveTypeId);

            Map<String, Object> payload = new HashMap<>();
            payload.put("leaveTypeId", leaveTypeId);
            payload.put("deactivationEffectiveDate", effectiveDateStr);

            dto.setPayload(payload);

            approvalService.submitForApproval(dto, maker, makerRole);

            return ResponseEntity.ok(
                    new ApiResponse<>(true,
                            "Request to deactivate leave type effective from " + effectiveDateStr + " has been submitted for approval.",
                            null)
            );
        }

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.DEACTIVATE_LEAVE_TYPE);
        dto.setEntityId(leaveTypeId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("leaveTypeId", leaveTypeId);
        payload.put("deactivationEffectiveDate", effectiveDateStr);

        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker, makerRole);

        return ResponseEntity.ok(
                new ApiResponse<>(true,
                        "Request to deactivate leave type effective from " + effectiveDateStr + " has been submitted for approval.",
                        null)
        );
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
