package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.swing.text.html.HTML;
import java.util.List;

@RestController
@RequestMapping("/api/leave")
public class LeaveTypeController {

    @Autowired
    LeaveTypeServiceInterface service;

    @GetMapping("/")
    public ResponseEntity<String> firstMethod(){
        return new ResponseEntity<>("Hello and Welcome to paves", HttpStatus.OK);
    }

    @PostMapping("/add-leave-type")
    public ResponseEntity<?> addLeaveType(@RequestBody LeaveType leaveType){
        LeaveType response = service.addLeaveType(leaveType);
        if(response == null){
            return new ResponseEntity<>(response, HttpStatus.NOT_ACCEPTABLE);
        }
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @GetMapping("/get-all-leave-types")
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes(){
        List<LeaveType> allLeaveTypesResponse =  service.getAllLeaveTypes();
        if (allLeaveTypesResponse == null){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(allLeaveTypesResponse, HttpStatus.FOUND);
    }
}
