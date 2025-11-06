package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.enums.LeaveRevokeStatus;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveRevokeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveRevokeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LeaveRevokeRequestService implements LeaveRevokeRequest {

    @Autowired
    private final LeaveRevokeRepo leaveRevokeRepo;

    @Autowired
    private final LeaveRequestRepo leaveRequestRepo;

    public LeaveRevokeRequestService(LeaveRevokeRepo leaveRevokeRepo, LeaveRequestRepo leaveRequestRepo) {
        this.leaveRevokeRepo = leaveRevokeRepo;
        this.leaveRequestRepo = leaveRequestRepo;
    }



    @Override
    public String newRevokeRequest(LeaveRevoke revokeRequest) {
        Optional<LeaveRequest> leaveRequest = leaveRequestRepo.findById(revokeRequest.getLeaveRequestId());

        if(leaveRequest.isEmpty()){
            throw new RuntimeException("Leave Type not found");
        }

        LeaveRevoke request = leaveRevokeRepo.findByLeaveRequestId(revokeRequest.getLeaveRequestId());
        if(request != null){
            throw new RuntimeException("Leave revoke request already exists");
        }
        revokeRequest.setStatus(LeaveRevokeStatus.PENDING);
        leaveRevokeRepo.save(revokeRequest);
        return "Leave revoke request submitted successfully";
    }


}
