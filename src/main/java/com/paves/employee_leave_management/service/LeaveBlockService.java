package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveBlockRequestDto;
import com.paves.employee_leave_management.dto.UnblockLeaveRequestDto;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.entities.LeaveBlockLeaveType;
import com.paves.employee_leave_management.entities.LeaveBlockMember;
import com.paves.employee_leave_management.enums.BlockStatus;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBlockException;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveBlockLeaveTypeRepo;
import com.paves.employee_leave_management.repo.LeaveBlockMemberRepo;
import com.paves.employee_leave_management.repo.LeaveBlockRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBlockServiceInterface;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LeaveBlockService implements LeaveBlockServiceInterface {

    @Autowired
    private LeaveBalanceRepo leaveBalanceRepo;
    @Autowired
    private LeaveBlockLeaveTypeRepo leaveBlockLeaveTypeRepo;
    @Autowired
    private LeaveBlockMemberRepo leaveBlockMemberRepo;
    @Autowired
    private LeaveBlockRepo leaveBlockRepo;

    // ------------------------------------------------------
    // CREATE LEAVE BLOCK
    // ------------------------------------------------------
    @Override
    @Transactional
    public void blockLeave(LeaveBlockRequestDto requestDto) {
        LocalDate today = LocalDate.now();

        // 🧩 1. Validate date inputs
        if (requestDto.getStartDate() == null || requestDto.getEndDate() == null) {
            throw new LeaveBlockException("Start date and end date are required.");
        }
        if (requestDto.getStartDate().isAfter(requestDto.getEndDate())) {
            throw new LeaveBlockException("Start date cannot be after end date.");
        }

        // 🧩 2. Validate members and leave types
        if (requestDto.getMembers() == null || requestDto.getMembers().isEmpty()) {
            throw new LeaveBlockException("At least one employee must be selected for blocking.");
        }
        if (requestDto.getLeaveTypeIds() == null || requestDto.getLeaveTypeIds().isEmpty()) {
            throw new LeaveBlockException("At least one leave type must be selected for blocking.");
        }

        // 🧩 3. Prevent overlapping blocks
        boolean overlapExists = leaveBlockRepo.existsByProjectIdAndDateRangeOverlap(
                requestDto.getProjectId(), requestDto.getStartDate(), requestDto.getEndDate());
        if (overlapExists) {
            throw new LeaveBlockException("A leave block already exists for this project within the selected date range.");
        }

        // 🧩 4. Determine status based on date
        BlockStatus initialStatus = requestDto.getStartDate().isAfter(today)
                ? BlockStatus.PENDING
                : BlockStatus.ACTIVE;

        // 🧩 5. Create and save LeaveBlock
        LeaveBlock leaveBlock = LeaveBlock.builder()
                .managerId(requestDto.getManagerId())
                .projectId(requestDto.getProjectId())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .status(initialStatus)
                .reason(requestDto.getReason())
                .createdAt(OffsetDateTime.now())
                .build();

        LeaveBlock savedLeaveBlock = leaveBlockRepo.save(leaveBlock);
        String blockId = savedLeaveBlock.getId();

        // 🧩 6. Save types & members
        saveBlockRelations(requestDto, savedLeaveBlock);

        // 🧩 7. If active now, block balances immediately
        if (!requestDto.getStartDate().isAfter(today)) {
            updateBalancesForBlock(requestDto, blockId, true);
        }

        System.out.println("Leave block created successfully with status: " + initialStatus);
    }

    // ------------------------------------------------------
    // UPDATE LEAVE BLOCK
    // ------------------------------------------------------
    @Transactional
    public LeaveBlock updateLeaveBlock(String blockId, LeaveBlockRequestDto requestDto) {
        LeaveBlock existingBlock = leaveBlockRepo.findById(blockId)
                .orElseThrow(() -> new RuntimeException("LeaveBlock not found with ID: " + blockId));

        // Update main block fields
        existingBlock.setManagerId(requestDto.getManagerId());
        existingBlock.setProjectId(requestDto.getProjectId());
        existingBlock.setStartDate(requestDto.getStartDate());
        existingBlock.setEndDate(requestDto.getEndDate());
        existingBlock.setReason(requestDto.getReason());
        existingBlock.setStatus(requestDto.getStatus());
        existingBlock.setUpdatedAt(OffsetDateTime.now());

        // --- Update Leave Types ---
        // 1. Delete old associations
        leaveBlockLeaveTypeRepo.deleteAllByLeaveBlock(existingBlock);

        // 2. Save new ones
        List<LeaveBlockLeaveType> updatedLeaveTypes = requestDto.getLeaveTypeIds().stream()
                .map(typeId -> LeaveBlockLeaveType.builder()
                        .id(UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase())
                        .leaveBlock(existingBlock)
                        .leaveTypeId(typeId)
                        .build())
                .toList();
        leaveBlockLeaveTypeRepo.saveAll(updatedLeaveTypes);

        // --- Update Members ---
        // 1. Delete old members
        leaveBlockMemberRepo.deleteAllByLeaveBlock(existingBlock);

        // 2. Save new ones
        List<LeaveBlockMember> updatedMembers = requestDto.getMembers().stream()
                .map(memberId -> LeaveBlockMember.builder()
                        .id(UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase())
                        .leaveBlock(existingBlock)
                        .employeeId(memberId)
                        .build())
                .toList();
        leaveBlockMemberRepo.saveAll(updatedMembers);

        // --- Update LeaveBalances ---
        // First, clear previous blocked balances if any
        List<LeaveBalance> previouslyBlockedBalances = leaveBalanceRepo.findByBlockId(blockId);
        previouslyBlockedBalances.forEach(balance -> {
            balance.setBlockId(null);
            balance.setIsBlocked(false);
        });
        leaveBalanceRepo.saveAll(previouslyBlockedBalances);

        // Now block the new balances (only if block is active)
        if (existingBlock.getStatus() == BlockStatus.ACTIVE) {
            List<LeaveBalance> newBalancesToBlock = new ArrayList<>();
            for (String employeeId : requestDto.getMembers()) {
                for (String leaveTypeId : requestDto.getLeaveTypeIds()) {
                    LeaveBalance balance = leaveBalanceRepo
                            .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, requestDto.getYear());
                    if (balance != null) {
                        balance.setBlockId(blockId);
                        balance.setIsBlocked(true);
                        newBalancesToBlock.add(balance);
                    }
                }
            }
            if (!newBalancesToBlock.isEmpty()) {
                leaveBalanceRepo.saveAll(newBalancesToBlock);
            }
        }

        return leaveBlockRepo.save(existingBlock);
    }


    // ------------------------------------------------------
    // UNBLOCK
    // ------------------------------------------------------
    @Override
    @Transactional
    public void unblockLeave(UnblockLeaveRequestDto requestDto) {
        String blockId = requestDto.getBlockId();

        LeaveBlock leaveBlock = leaveBlockRepo.findById(blockId)
                .orElseThrow(() -> new LeaveBlockException("Leave block not found with ID: " + blockId));

        List<LeaveBalance> balances = leaveBalanceRepo.findByBlockId(blockId);
        if (balances.isEmpty()) {
            throw new LeaveBlockException("No balances found to unblock for this leave block.");
        }

        balances.forEach(b -> {
            b.setBlockId(null);
            b.setIsBlocked(false);
        });
        leaveBalanceRepo.saveAll(balances);

        leaveBlock.setStatus(BlockStatus.INACTIVE);
        leaveBlockRepo.save(leaveBlock);
    }

    // ------------------------------------------------------
    // SUPPORT METHODS
    // ------------------------------------------------------
    private void saveBlockRelations(LeaveBlockRequestDto dto, LeaveBlock block) {
        List<LeaveBlockLeaveType> types = dto.getLeaveTypeIds().stream()
                .map(id -> LeaveBlockLeaveType.builder()
                        .id(UUID.randomUUID().toString().substring(0, 5).toUpperCase())
                        .leaveBlock(block)
                        .leaveTypeId(id)
                        .build())
                .toList();
        leaveBlockLeaveTypeRepo.saveAll(types);

        List<LeaveBlockMember> members = dto.getMembers().stream()
                .map(eid -> LeaveBlockMember.builder()
                        .id(UUID.randomUUID().toString().substring(0, 5).toUpperCase())
                        .leaveBlock(block)
                        .employeeId(eid)
                        .build())
                .toList();
        leaveBlockMemberRepo.saveAll(members);
    }

    private void updateBalancesForBlock(LeaveBlockRequestDto dto, String blockId, boolean isBlock) {
        List<LeaveBalance> balancesToUpdate = new ArrayList<>();

        for (String empId : dto.getMembers()) {
            for (String typeId : dto.getLeaveTypeIds()) {
                LeaveBalance balance = leaveBalanceRepo
                        .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(empId, typeId, dto.getYear());
                if (balance != null) {
                    balance.setBlockId(isBlock ? blockId : null);
                    balance.setIsBlocked(isBlock);
                    balancesToUpdate.add(balance);
                }
            }
        }

        if (!balancesToUpdate.isEmpty()) {
            leaveBalanceRepo.saveAll(balancesToUpdate);
        }
    }

    @Override
    public List<LeaveBlock> getAllActiveLeaveBlock(String managerId) {
        return leaveBlockRepo.findByManagerIdAndStatus(managerId, BlockStatus.ACTIVE);
    }

    @Override
    public List<LeaveBlock> getAllLeaveBlocks() {
        return leaveBlockRepo.findAll();
    }

    @Override
    public void deActivateLeaveBlock(String blockId) {
        LeaveBlock block = leaveBlockRepo.findById(blockId)
                .orElseThrow(() -> new LeaveBlockException("Leave block not found with ID: " + blockId));

        if (block.getStatus() == BlockStatus.INACTIVE) {
            throw new LeaveBlockException("Leave block is already inactive.");
        }

        List<LeaveBalance> balances = leaveBalanceRepo.findByBlockId(blockId);
        balances.forEach(b -> {
            b.setBlockId(null);
            b.setIsBlocked(false);
        });
        leaveBalanceRepo.saveAll(balances);

        block.setStatus(BlockStatus.INACTIVE);
        leaveBlockRepo.save(block);
    }
}
