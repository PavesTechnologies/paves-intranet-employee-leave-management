package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveTypeServiceImple implements LeaveTypeServiceInterface {

    @Autowired
    LeaveTypeRepo repo;

    @Override
    public ResponseEntity<LeaveType> addLeaveType(LeaveType leaveType) {
        Optional<LeaveType> leaveRes = repo.findByLeaveTypeId(leaveType.getLeaveTypeId());
        if(leaveRes.isEmpty()){
            return new ResponseEntity<>(repo.save(leaveType), HttpStatus.OK);
        }
        return new ResponseEntity<>(repo.save(leaveType), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
         List<LeaveType> allLeaveTypes = repo.findAll();
         if(allLeaveTypes.isEmpty()){
             return new ResponseEntity<>(HttpStatus.NO_CONTENT);
         }
         return new ResponseEntity<>(allLeaveTypes, HttpStatus.FOUND);
    }

    @Override
    public ResponseEntity<LeaveType> updateLeaveType(LeaveType leaveType) {
        Optional<LeaveType> leaveTypeRes = repo.findByLeaveTypeId(leaveType.getLeaveTypeId());
        if (leaveTypeRes.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else
            return new ResponseEntity<>(repo.save(leaveType), HttpStatus.ACCEPTED);
    }
}
