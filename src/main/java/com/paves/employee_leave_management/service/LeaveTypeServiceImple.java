package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaveTypeServiceImple implements LeaveTypeServiceInterface {

    @Autowired
    LeaveTypeRepo repo;

    @Override
    public LeaveType addLeaveType(LeaveType leaveType) {
        LeaveType response = repo.save(leaveType);
        if (response == null){
            return null;
        }
        return response;
    }

    @Override
    public List<LeaveType> getAllLeaveTypes() {
         List<LeaveType> allLeaveTypes = repo.findAll();
         if(allLeaveTypes.isEmpty()){
             return null;
         }
         return allLeaveTypes;
    }
}
