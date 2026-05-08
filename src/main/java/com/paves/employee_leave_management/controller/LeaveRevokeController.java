package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveRevokeDTO;
import com.paves.employee_leave_management.dto.LeaveWebSocketEvent;
import com.paves.employee_leave_management.dto.RevokeRequestDTO;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.enums.WsEventType;
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
        LeaveRevoke response = leaveRevokeRequestService.newRevokeRequest(revokeRequest);

        // ✅ Notify manager — new revoke request appeared in their queue
        LeaveWebSocketEvent event = new LeaveWebSocketEvent(
                WsEventType.REVOKE_REQUESTED.name(),
                revokeRequest.getLeaveRequestId(),
                revokeRequest.getEmployeeId(),
                revokeRequest.getManagerId()   // manager needs to see it
        );

        // ✅ Broadcast to manager's topic — their pending list should refresh
        template.convertAndSend("/topic/manager/leave-requests", event);

        if(response != null ){
            return new ApiResponse<>(true, "Leave revoke request submitted successfully", null);
        }
        return new ApiResponse<>(true,"Leave Revoke Request Failed to Submit" , null);
    }

    @PostMapping("/approve/{revokeId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<String> approveRequest(@PathVariable String revokeId, @RequestBody RevokeRequestDTO revokeRequestDTO) {
        leaveRevokeRequestService.approveRequest(revokeId, revokeRequestDTO);

        LeaveWebSocketEvent event = new LeaveWebSocketEvent(
                WsEventType.REVOKE_APPROVED.name(),
                revokeId,
                revokeRequestDTO.getEmployeeId()
        );

        template.convertAndSendToUser(revokeRequestDTO.getEmployeeId(), "/queue/data-updated", event);
        return new ApiResponse<>(true, "Leave revoke request approved successfully", null);
    }

    @GetMapping("/pending/{managerId}")
    @PreAuthorize("hasRole('MANAGER') and @permissionService.isOwner(authentication, #managerId)")
    public ApiResponse<List<LeaveRevokeDTO>> getPendingRequests(@PathVariable String managerId) {
        List<LeaveRevokeDTO> pendingRequests = leaveRevokeRequestService.getPendingRequests(managerId);
//        template.convertAndSend("/topic/leave-updated", "updated");
        return new ApiResponse<>(true, "Pending requests retrieved successfully", pendingRequests);
    }

    @PostMapping("/reject/{revokeId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<String> rejectRequest(@PathVariable String revokeId) {
        LeaveRevoke revoke =leaveRevokeRequestService.rejectRequest(revokeId);

        LeaveWebSocketEvent event = new LeaveWebSocketEvent(
                WsEventType.REVOKE_REJECTED.name(),
                revokeId,
                revoke.getEmployeeId()
        );

        template.convertAndSendToUser(revoke.getEmployeeId(), "/topic/leave-updated", event);
        return new ApiResponse<>(true, "Leave revoke request rejected successfully", null);
    }
}
