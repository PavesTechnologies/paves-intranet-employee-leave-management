package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveBlockRequestDto;

import com.paves.employee_leave_management.dto.UnblockLeaveRequestDto;
import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.serviceInterface.LeaveBlockServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/leave-block")
public class LeaveBlockController {

    @Autowired
    private LeaveBlockServiceInterface leaveBlockService;

    @PostMapping("/block")
    public ApiResponse<String> blockLeave(@RequestBody LeaveBlockRequestDto requestDto) {
        leaveBlockService.blockLeave(requestDto);
        return new ApiResponse<>(true, "Leave block request submitted successfully", null);

    }

    @GetMapping("/blocked-leaves/{managerId}")
    public ApiResponse<List<LeaveBlock>> getAllActiveBlockedLeaves(@PathVariable String managerId) {
        List<LeaveBlock> leaveBlocks = leaveBlockService.getAllActiveLeaveBlock(managerId);
        return new ApiResponse<>(true, "Leave blocks retrieved successfully", leaveBlocks);
    }

    @PostMapping("/unblock")
    public ApiResponse<String> unblockLeave(@RequestBody UnblockLeaveRequestDto requestDto) {
        leaveBlockService.unblockLeave(requestDto);
        return new ApiResponse<>(true, "Leave unblock request submitted successfully", null);
    }

    @PutMapping("/update/{blockId}")
    public ApiResponse<String> updateLeaveBlock(@PathVariable String blockId, @RequestBody LeaveBlockRequestDto requestDto) {
        leaveBlockService.updateLeaveBlock(blockId, requestDto);
        return new ApiResponse<>(true, "Leave block updated successfully", null);
    }

    @GetMapping("/leave-blocked")
    public ApiResponse<List<LeaveBlock>> getAllLeaveBlocks() {
        List<LeaveBlock> leaveBlocks = leaveBlockService.getAllLeaveBlocks();
        return new ApiResponse<>(true, "Leave blocks retrieved successfully", leaveBlocks);
    }

    @PostMapping("/deactivate")
    public ApiResponse<String> deActivateLeaveBlock(@RequestBody String blockId) {
        leaveBlockService.deActivateLeaveBlock(blockId);
        return new ApiResponse<>(true, "Leave block deactivated successfully", null);
    }


}
