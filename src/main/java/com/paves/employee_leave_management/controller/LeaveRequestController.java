package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestServiceInterface leaveRequestService;

    @GetMapping("/pending/{managerId}")
    public ResponseEntity<List<LeaveRequest>> getPending(@PathVariable String managerId) {
        return ResponseEntity.ok(leaveRequestService.getPendingRequestsForManager(managerId));
    }

    @PutMapping("/approve/{leaveId}")
    public ResponseEntity<LeaveRequest> approveRequest(@PathVariable String leaveId, @RequestParam String managerId) {
        return ResponseEntity.ok(leaveRequestService.approveRequest(leaveId, managerId));
    }

    @PutMapping("/reject/{leaveId}")
    public ResponseEntity<LeaveRequest> rejectRequest(@PathVariable String leaveId,
                                                      @RequestParam String managerId,
                                                      @RequestParam String comment) {
        return ResponseEntity.ok(leaveRequestService.rejectRequest(leaveId, managerId, comment));
    }

    @PutMapping("/update-leave-request")
    public ResponseEntity<ValidationResultDTO> updateRequest(@RequestBody LeaveRequest leaveRequest) {
        return ResponseEntity.ok(leaveRequestService.updateRequest(leaveRequest));
    }
}

