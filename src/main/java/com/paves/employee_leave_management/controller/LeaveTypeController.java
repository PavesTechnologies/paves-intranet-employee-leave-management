package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@CrossOrigin
@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    LeaveTypeServiceInterface service;

    @PostMapping("/add-leave-type")
    public ResponseEntity<LeaveType> addLeaveType(@RequestBody LeaveType leaveType){
        return service.addLeaveType(leaveType);
    }

    @GetMapping("/get-all-leave-types")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes(){
        return service.getAllLeaveTypes();
    }

//    @PatchMapping("/update-leave-type/{leaveTypeId}")
    @PatchMapping("/update-leave-type/")
    public void updateLeave(@RequestBody LeaveType leaveTypeDto){
         service.updateLeaveType(leaveTypeDto);
    }

    @DeleteMapping("/delete-leave-type/{leaveTypeId}")
    public void deleteLeaveType(@PathVariable String leaveTypeId){
        service.deleteLeaveType(leaveTypeId);
    }
}
