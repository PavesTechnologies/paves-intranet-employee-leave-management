package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveRevokeDTO;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.service.LeaveRevokeRequestService;
import com.paves.employee_leave_management.serviceInterface.LeaveRevokeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-revoke")
public class LeaveRevokeController {

    @Autowired
    private final LeaveRevokeRequest leaveRevokeRequestService;

    @Autowired
    private final SimpMessagingTemplate template;

    public LeaveRevokeController(LeaveRevokeRequestService leaveRevokeRequestService, SimpMessagingTemplate template) {
        this.leaveRevokeRequestService = leaveRevokeRequestService;
        this.template = template;
    }

    @PostMapping("/revoke")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER', 'GENERAL', 'HR_MANAGER')")
    public ApiResponse<String> newRevokeRequest(@RequestBody LeaveRevoke revokeRequest) {
        String responds = leaveRevokeRequestService.newRevokeRequest(revokeRequest);
        template.convertAndSend("/topic/leave-update", "updated");
        return new ApiResponse<>(true, responds, null);
    }

    @PostMapping("/approve/{revokeId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<String> approveRequest(@PathVariable String revokeId) {
        leaveRevokeRequestService.approveRequest(revokeId);
        template.convertAndSend("/topic/leave-update", "updated");
        return new ApiResponse<>(true, "Leave revoke request approved successfully", null);
    }

    @GetMapping("/pending/{managerId}")
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isOwner(authentication, #managerId)")
    public ApiResponse<List<LeaveRevokeDTO>> getPendingRequests(@PathVariable String managerId) {
        List<LeaveRevokeDTO> pendingRequests = leaveRevokeRequestService.getPendingRequests(managerId);
        template.convertAndSend("/topic/leave-update", "updated");
        return new ApiResponse<>(true, "Pending requests retrieved successfully", pendingRequests);
    }

    @PostMapping("/reject/{revokeId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<String> rejectRequest(@PathVariable String revokeId) {
        leaveRevokeRequestService.rejectRequest(revokeId);
        template.convertAndSend("/topic/leave-update", "updated");
        return new ApiResponse<>(true, "Leave revoke request rejected successfully", null);
    }


}
