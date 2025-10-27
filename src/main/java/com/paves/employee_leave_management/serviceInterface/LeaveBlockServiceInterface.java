package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveBlockRequestDto;
import com.paves.employee_leave_management.dto.UnblockLeaveRequestDto;
import com.paves.employee_leave_management.entities.LeaveBlock;

import java.time.OffsetDateTime;
import java.util.List;

public interface LeaveBlockServiceInterface {
    void blockLeave(LeaveBlockRequestDto requestDto);
    List<LeaveBlock> getAllActiveLeaveBlock(String managerId);
    void unblockLeave(UnblockLeaveRequestDto requestDto);
    LeaveBlock updateLeaveBlock(String blockId, LeaveBlockRequestDto requestDto);

    List<LeaveBlock> getAllLeaveBlocks();

    void deActivateLeaveBlock(String blockId);
}
