package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.entities.LeaveTypesEnum;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


@CrossOrigin
@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    LeaveTypeServiceInterface service;

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

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
    public ApiResponse<LeaveType> addLeaveType(@RequestBody LeaveType leaveType){
        return service.addLeaveType(leaveType);
    }

    @GetMapping("/get-all-leave-types")
    @PreAuthorize("hasAnyRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes(){
        return service.getAllLeaveTypes();
    }

//    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<LeaveType> updateLeave(@RequestBody LeaveType leaveTypeDto, @PathVariable String leaveTypeId){
         return service.updateLeaveType(leaveTypeDto, leaveTypeId);
    }

    @DeleteMapping("/delete-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteLeaveType(@PathVariable String leaveTypeId){
        return service.deActiveLeaveType(leaveTypeId);
    }
    // Upload document
    @PreAuthorize("hasRole('HR')")
    @PostMapping("/{leaveTypeId}/upload-document")
    public ResponseEntity<String> uploadDocument(@PathVariable String leaveTypeId,
                                                 @RequestParam("file") MultipartFile file) throws Exception {
        service.uploadDocument(leaveTypeId, file);
        return ResponseEntity.ok("Document uploaded successfully");
    }

    // View document
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

    // Delete document
    @PreAuthorize("hasRole('HR')")
    @DeleteMapping("/{leaveTypeId}/document")
    public ResponseEntity<String> deleteDocument(@PathVariable String leaveTypeId) throws Exception {
        service.deleteDocument(leaveTypeId);
        return ResponseEntity.ok("Document deleted successfully");
    }

}
