package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.AccrualFrequency;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceJobRepository;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceJobServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/leave")
@Tag(name = "Leave Type", description = "APIs for leave management")
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

    @Autowired
    private LeaveBalanceJobServiceInterface leaveBalanceJobService;

    @Autowired
    private GenderBasedLeaveServiceInterface genderBaseLeaveService;

    @Autowired
    private LeaveTypeServiceInterface leaveTypeService;

    // This is a placeholder for getting the user from the JWT token
    private Object getAuthenticatedUser() {
        // In a real application, you would extract the user details from the Spring Security Context.
        // For now, we'll fetch a hardcoded user to simulate this.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long employeeId = jwt.getClaim("user_id");

        Optional<Employee> employee =
                employeeRepo.findByEmployeeId(String.valueOf(employeeId));

        if (employee.isPresent()) {
            return employee.get();
        }

        List<String> roles = jwt.getClaim("roles");

        return new AdminMaker(
                employeeId.toString(),
                roles.contains("Super_Admin")
                        ? "Super_Admin"
                        : "Admin"
        );
    }


    private String getMakerRole(Authentication authentication){
        return  authentication.getAuthorities().
                stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth->auth.replace("ROLE_", ""))
                .findFirst()
                .orElse("HR");
    }

    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL', 'SUPER_ADMIN')")
    public List<Map<String, String>> getLeaveTypes(Authentication authentication) {
        return service.getLeaveTypes();
    }

    @GetMapping("/accrual-frequencies")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public List<String> getAccrualFrequencies() {
        return Arrays.stream(AccrualFrequency.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }


    @PostMapping("/add-leave-type")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> addLeaveType(@Valid @RequestBody LeaveType leaveType, Authentication authentication) {
        Object maker = getAuthenticatedUser();
        // Assuming the role is stored in the jobTitle field for now
        // String makerRole = maker.getJobTitle();

        String makerRole = getMakerRole(authentication);


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


        if (maker instanceof AdminMaker adminMaker) {
            return service.createDirectly(leaveType, adminMaker);
        } else if (maker instanceof Employee employee) {
            MCApprovalRequestDto dto = new MCApprovalRequestDto();
            dto.setActionType(ActionType.CREATE_LEAVE_TYPE);

            Map<String, Object> payload = new HashMap<>();
            payload.put("newData", leaveType);
            dto.setPayload(payload);

            approvalService.submitForApproval(dto, employee, makerRole);
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Request to add leave type has been submitted for approval.", null));
    }




    @GetMapping("/leave-balance-job/{jobId}")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getJobProgress(@PathVariable String jobId) {
        LeaveBalanceJob job = leaveBalanceJobService.getJobStatus(jobId);

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("jobId", job.getJobId());
        progress.put("leaveTypeName", job.getLeaveTypeName());
        progress.put("status", job.getStatus());
        progress.put("totalEmployees", job.getTotalEmployees());
        progress.put("processedEmployees", job.getProcessedEmployees());
        progress.put("progressPercentage", job.getProgressPercentage());
        progress.put("errorMessage", job.getErrorMessage());
        progress.put("startedAt", job.getStartedAt());
        progress.put("completedAt", job.getCompletedAt());

        return ResponseEntity.ok(new ApiResponse<>(true, "Job status", progress));
    }

    @GetMapping("/get-all-leave-types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL', 'SUPER_ADMIN')")
    public ResponseEntity<AllLeaveTypesListResponseDTO> getAllLeaveTypes() {
         AllLeaveTypesListResponseDTO allLeaveTypesListResponseDTO =  service.getAllLeaveTypes();
         if(allLeaveTypesListResponseDTO == null){
             return new ResponseEntity<>(HttpStatus.NO_CONTENT);
         }
         return ResponseEntity.ok(allLeaveTypesListResponseDTO);
    }

    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> updateLeave(@PathVariable String leaveTypeId, @RequestBody UpdateLeaveRequest request, Authentication authentication) {
        String makerRole = getMakerRole(authentication);
        Employee maker = null;
        if(!("ADMIN".equalsIgnoreCase(makerRole) || "SUPER_ADMIN".equalsIgnoreCase(makerRole))){
            maker = (Employee)getAuthenticatedUser();
        }

//        String makerRole = maker.getJobTitle();

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
                if(maker == null){
                    genderBaseLeaveService.updateGenderBaseLeave(genderBasedLeave, toUpdate.getLeaveTypeId());
                    return ResponseEntity.ok(new ApiResponse<>(true, "Request to update leave type by " + makerRole + " has been submitted for approval.", null));
                }
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

        if(maker == null){
            leaveTypeService.updateLeaveType(leaveType, leaveTypeId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Request to update leave type by " + makerRole + " has been submitted for approval.", null));
        }

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
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> deleteLeaveType(
            @PathVariable String leaveTypeId,
            @RequestBody Map<String, String> requestBody,
            Authentication authentication) {

        String makerRole = getMakerRole(authentication);
        Employee maker =  null;
        if (!("ADMIN".equalsIgnoreCase(makerRole) || "SUPER_ADMIN".equalsIgnoreCase(makerRole))){
            maker = (Employee) getAuthenticatedUser();
        }


        String effectiveDateStr = requestBody.get("deactivationEffectiveDate");
        LocalDate effectiveDate = LocalDate.parse(
                requestBody.get("deactivationEffectiveDate"));


        // Optional: validate input
        if (effectiveDateStr == null || effectiveDateStr.isEmpty()) {
            throw new IllegalArgumentException("Deactivation effective date is required.");
        }

        Optional<GenderBasedLeave> genderBasedLeave = genderBasedRepo.findById(leaveTypeId);
        Optional<LeaveType> leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);
        if(genderBasedLeave.isEmpty() && leaveType.isEmpty()){
            return ResponseEntity.ok(new ApiResponse<>(false, "Leave type not found", null));
        }

        if(genderBasedLeave.isPresent() && genderBasedLeave.get().getLeaveTypeId().equals(leaveTypeId)){
            if(maker == null){
                genderBaseLeaveService.deActiveGenderBaseLeaveType(leaveTypeId, effectiveDate);
                return ResponseEntity.ok(new ApiResponse<>(true, "Request to deactivate leave type by " + makerRole + " has been submitted for approval.", null));
            }
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

        if(maker == null){
            leaveTypeService.deActiveLeaveType(leaveTypeId, effectiveDate);
            return ResponseEntity.ok(new ApiResponse<>(true, "Request to deactivate leave type by " + makerRole + " has been submitted for approval.", null));
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
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL', 'SUPER_ADMIN')")
    public ResponseEntity<List<LeaveTypeIdDTO>> getAllLeaveTypeIds() {
        return new ResponseEntity<>(service.getAllLeaveTypeIds(), HttpStatus.OK);
    }

}
