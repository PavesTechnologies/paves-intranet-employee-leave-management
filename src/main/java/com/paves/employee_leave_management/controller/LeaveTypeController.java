package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 
 */
@CrossOrigin
@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    LeaveTypeServiceInterface service;

    @PostMapping("/add-leave-type")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<LeaveType> addLeaveType(@RequestBody LeaveType leaveType){
        return service.addLeaveType(leaveType);
    }

    @GetMapping("/get-all-leave-types")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes(){
        return service.getAllLeaveTypes();
    }

    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PreAuthorize("hasRole('HR')")
    public void updateLeave(@PathVariable String leaveTypeId, @RequestBody LeaveTypeDto leaveTypeDto){
         service.updateLeaveType(leaveTypeId);
    }
}
