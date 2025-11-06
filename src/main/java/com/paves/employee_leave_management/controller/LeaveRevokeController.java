package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.service.LeaveRevokeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leave-revoke")
public class LeaveRevokeController {

    @Autowired
    private final LeaveRevokeRequestService leaveRevokeRequestService;

    public LeaveRevokeController(LeaveRevokeRequestService leaveRevokeRequestService) {
        this.leaveRevokeRequestService = leaveRevokeRequestService;
    }

    @PostMapping("/revoke")
    public ApiResponse<String> newRevokeRequest(@RequestBody LeaveRevoke revokeRequest){
        String responds = leaveRevokeRequestService.newRevokeRequest(revokeRequest);
        return new ApiResponse<>(true, responds, null);
    }



}
