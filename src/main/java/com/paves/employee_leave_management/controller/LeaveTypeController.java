package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.entities.LeaveTypesEnum;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.repository.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.service.ApprovalService;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

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

    @PostMapping("/add-leave-type")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> addLeaveType(@RequestBody LeaveType leaveType) {
        // In a real app, get the maker from the security context.
        Employee maker = employeeRepo.findById("PAVEMP45179").orElseThrow(() -> new RuntimeException("Maker not found"));

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.CREATE_LEAVE_TYPE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("newData", leaveType);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker);

        return ResponseEntity.ok("Request to add leave type has been submitted for approval.");
    }

    @GetMapping("/get-all-leave-types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        return service.getAllLeaveTypes();
    }

    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> updateLeave(@RequestBody LeaveType updatedLeaveType, @PathVariable String leaveTypeId) {
        Employee maker = employeeRepo.findById("PAVEMP45179").orElseThrow(() -> new RuntimeException("Maker not found"));
        LeaveType oldLeaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId).orElseThrow(() -> new RuntimeException("LeaveType not found"));

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.UPDATE_LEAVE_TYPE);
        dto.setEntityId(leaveTypeId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("before", oldLeaveType);
        payload.put("after", updatedLeaveType);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker);

        return ResponseEntity.ok("Request to update leave type has been submitted for approval.");
    }

    @DeleteMapping("/delete-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteLeaveType(@PathVariable String leaveTypeId) {
        Employee maker = employeeRepo.findById("PAVEMP45179").orElseThrow(() -> new RuntimeException("Maker not found"));

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.DEACTIVATE_LEAVE_TYPE);
        dto.setEntityId(leaveTypeId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("leaveTypeId", leaveTypeId);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker);

        return ResponseEntity.ok("Request to deactivate leave type has been submitted for approval.");
    }

    // Document management endpoints remain unchanged as they are not part of the approval workflow

    @PreAuthorize("hasRole('HR')")
    @PostMapping("/{leaveTypeId}/upload-document")
    public ResponseEntity<String> uploadDocument(@PathVariable String leaveTypeId,
                                                 @RequestParam("file") MultipartFile file) throws Exception {
        service.uploadDocument(leaveTypeId, file);
        return ResponseEntity.ok("Document uploaded successfully");
    }

    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    @GetMapping("/{leaveTypeName}/document")
    public ResponseEntity<ByteArrayResource> viewDocument(@PathVariable String leaveTypeName,
                                                          @RequestParam(defaultValue = "pdf") String fileType) throws Exception {
        byte[] data = service.viewDocument(leaveTypeName, fileType);

        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"policy." + fileType + "\"")
                .contentType(MediaType.parseMediaType(service.getMimeType(fileType)))
                .contentLength(data.length)
                .body(resource);
    }

    @PreAuthorize("hasRole('HR')")
    @DeleteMapping("/{leaveTypeId}/document")
    public ResponseEntity<String> deleteDocument(@PathVariable String leaveTypeId) throws Exception {
        service.deleteDocument(leaveTypeId);
        return ResponseEntity.ok("Document deleted successfully");
    }
}
