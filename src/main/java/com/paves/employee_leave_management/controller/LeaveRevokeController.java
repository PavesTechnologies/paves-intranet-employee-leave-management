package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveRevokeDTO;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.service.LeaveRevokeRequestService;
import com.paves.employee_leave_management.serviceInterface.LeaveRevokeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-revoke")
public class LeaveRevokeController {

    @Autowired
    private final LeaveRevokeRequest leaveRevokeRequestService;

    public LeaveRevokeController(LeaveRevokeRequestService leaveRevokeRequestService) {
        this.leaveRevokeRequestService = leaveRevokeRequestService;
    }

    @PostMapping("/revoke")
    public ApiResponse<String> newRevokeRequest(@RequestBody LeaveRevoke revokeRequest){
        String responds = leaveRevokeRequestService.newRevokeRequest(revokeRequest);
        return new ApiResponse<>(true, responds, null);
    }

    @PostMapping("/approve/{revokeId}")
    public ApiResponse<String> approveRequest(@PathVariable String revokeId){
        leaveRevokeRequestService.approveRequest(revokeId);
        return new ApiResponse<>(true, "Leave revoke request approved successfully", null);
    }

    @GetMapping("/pending/{managerId}")
    public ApiResponse<List<LeaveRevokeDTO>> getPendingRequests(@PathVariable String managerId){
        List<LeaveRevokeDTO> pendingRequests = leaveRevokeRequestService.getPendingRequests(managerId);
        return new ApiResponse<>(true, "Pending requests retrieved successfully", pendingRequests);
    }

    @PostMapping("/reject/{revokeId}")
    public ApiResponse<String> rejectRequest(@PathVariable String revokeId){
        leaveRevokeRequestService.rejectRequest(revokeId);
        return new ApiResponse<>(true, "Leave revoke request rejected successfully", null);
    }



}
