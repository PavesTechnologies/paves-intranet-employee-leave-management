package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.entities.LeaveTypesEnum;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


@CrossOrigin
@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    LeaveTypeServiceInterface service;

    @GetMapping("/types")
    @PreAuthorize("hasRole('HR','MANAGER','GENERAL')")
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
    @PreAuthorize("hasRole('HR','MANAGER','GENERAL')")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes(){
        return service.getAllLeaveTypes();
    }

//    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PatchMapping("/update-leave-type/")
    @PreAuthorize("hasRole('HR')")
    public void updateLeave(@RequestBody LeaveType leaveTypeDto){
         service.updateLeaveType(leaveTypeDto);
    }

    @DeleteMapping("/delete-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteLeaveType(@PathVariable String leaveTypeId){
        return service.deleteLeaveType(leaveTypeId);
    }
}
