package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveBlockRequestDto;
import com.paves.employee_leave_management.dto.UnblockLeaveRequestDto;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.BlockStatus;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBlockException;
import com.paves.employee_leave_management.repo.*;
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

    @Autowired
    private LeaveBlockMappingRepo leaveBlockMappingRepo;

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
        boolean overlapExists = leaveBlockRepo.existsByProjectIdAndDateRangeOverlapAndStatus(
                requestDto.getProjectId(),
                requestDto.getStartDate(),
                requestDto.getEndDate(),
                BlockStatus.ACTIVE
        );
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
                .year(today.getYear())
                .build();

        LeaveBlock savedLeaveBlock = leaveBlockRepo.save(leaveBlock);

        // 🧩 6. Save Members and Leave Types (existing functionality)
        saveBlockRelations(requestDto, savedLeaveBlock);
        updateBalancesForBlock(requestDto, savedLeaveBlock.getId(), true);


        // 🆕 7. Save Employee ↔ LeaveType Mappings
        List<LeaveBlockMapping> mappings = new ArrayList<>();

        for (String empId : requestDto.getMembers()) {
            for (String leaveTypeId : requestDto.getLeaveTypeIds()) {
                LeaveBlockMapping mapping = LeaveBlockMapping.builder()
                        .leaveBlock(savedLeaveBlock)
                        .employeeId(empId)
                        .leaveTypeId(leaveTypeId)
                        .year(today.getYear())
                        .status(initialStatus)
                        .build();
                mappings.add(mapping);
            }
        }

        leaveBlockMappingRepo.saveAll(mappings);

        // 🧩 8. If active now, block balances immediately
        if (!requestDto.getStartDate().isAfter(today)) {
            updateBalancesForBlock(requestDto, savedLeaveBlock.getId(), true);
        }

        System.out.println("✅ Leave block created successfully with status: " + initialStatus);
    }


    // ------------------------------------------------------
    // UPDATE LEAVE BLOCK
    // ------------------------------------------------------
    @Transactional
    public LeaveBlock updateLeaveBlock(String blockId, LeaveBlockRequestDto requestDto) {
        LeaveBlock existingBlock = leaveBlockRepo.findById(blockId)
                .orElseThrow(() -> new LeaveBlockException("LeaveBlock not found with ID: " + blockId));

        // --- 1️⃣ Update main fields ---
        existingBlock.setManagerId(requestDto.getManagerId());
        existingBlock.setProjectId(requestDto.getProjectId());
        existingBlock.setStartDate(requestDto.getStartDate());
        existingBlock.setEndDate(requestDto.getEndDate());
        existingBlock.setReason(requestDto.getReason());
        existingBlock.setStatus(requestDto.getStatus());
        existingBlock.setUpdatedAt(OffsetDateTime.now());

        // --- 2️⃣ Update Leave Types (legacy support) ---
        leaveBlockLeaveTypeRepo.deleteAllByLeaveBlock(existingBlock);

        List<LeaveBlockLeaveType> updatedLeaveTypes = requestDto.getLeaveTypeIds().stream()
                .map(typeId -> LeaveBlockLeaveType.builder()
                        .id(UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase())
                        .leaveBlock(existingBlock)
                        .leaveTypeId(typeId)
                        .build())
                .toList();
        leaveBlockLeaveTypeRepo.saveAll(updatedLeaveTypes);

        // --- 3️⃣ Update Members (legacy support) ---
        leaveBlockMemberRepo.deleteAllByLeaveBlock(existingBlock);

        List<LeaveBlockMember> updatedMembers = requestDto.getMembers().stream()
                .map(memberId -> LeaveBlockMember.builder()
                        .id(UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase())
                        .leaveBlock(existingBlock)
                        .employeeId(memberId)
                        .build())
                .toList();
        leaveBlockMemberRepo.saveAll(updatedMembers);

        // --- 4️⃣ Update new Employee ↔ LeaveType mappings ---
        leaveBlockMappingRepo.deleteAllByLeaveBlock(existingBlock);

        List<LeaveBlockMapping> updatedMappings = new ArrayList<>();
        for (String empId : requestDto.getMembers()) {
            for (String leaveTypeId : requestDto.getLeaveTypeIds()) {
                LeaveBlockMapping mapping = LeaveBlockMapping.builder()
                        .leaveBlock(existingBlock)
                        .employeeId(empId)
                        .leaveTypeId(leaveTypeId)
                        .year(requestDto.getYear())
                        .build();
                updatedMappings.add(mapping);
            }
        }
        leaveBlockMappingRepo.saveAll(updatedMappings);

        // --- 5️⃣ Reset previous leave balances ---
        List<LeaveBalance> previouslyBlockedBalances = leaveBalanceRepo.findByBlockId(blockId);
        previouslyBlockedBalances.forEach(balance -> {
            balance.setBlockId(null);
            balance.setIsBlocked(false);
        });
        leaveBalanceRepo.saveAll(previouslyBlockedBalances);

        // --- 6️⃣ Re-block balances if ACTIVE ---
        if (existingBlock.getStatus() == BlockStatus.ACTIVE) {
            List<LeaveBalance> newBalancesToBlock = new ArrayList<>();

            for (String empId : requestDto.getMembers()) {
                for (String leaveTypeId : requestDto.getLeaveTypeIds()) {
                    LeaveBalance balance = leaveBalanceRepo
                            .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(empId, leaveTypeId, requestDto.getYear());
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

        for (var unblockReq : requestDto.getUnblockRequests()) {
            String employeeId = unblockReq.getEmployeeId();
            List<String> leaveTypeIdsToUnblock = unblockReq.getLeaveTypeIds();

            if (leaveTypeIdsToUnblock == null || leaveTypeIdsToUnblock.isEmpty()) {
                continue;
            }

            // 1) bulk update leave balances
            int balancesUpdated = leaveBalanceRepo.unblockBalancesForEmployeeAndTypes(employeeId, blockId, leaveTypeIdsToUnblock);
            System.out.println("Balances updated: " + balancesUpdated);

            // 2) mark mappings inactive (bulk)
            int mappingsUpdated = leaveBlockMappingRepo.markMappingsInactive(BlockStatus.INACTIVE, blockId, employeeId, leaveTypeIdsToUnblock);
            System.out.println("Mappings set inactive: " + mappingsUpdated);

            // 3) remove member if no mappings left
            boolean hasMappingsForEmployee = leaveBlockMappingRepo.existsByLeaveBlockIdAndEmployeeId(blockId, employeeId);
            if (!hasMappingsForEmployee) {
                leaveBlockMemberRepo.deleteByLeaveBlockIdAndEmployeeId(blockId, employeeId);
            }
        }

        // 4) remove leave types with no mappings
        List<LeaveBlockLeaveType> blockLeaveTypes = leaveBlockLeaveTypeRepo.findByLeaveBlock(leaveBlock);
        for (LeaveBlockLeaveType blockLeaveType : blockLeaveTypes) {
            String ltId = blockLeaveType.getLeaveTypeId();
            boolean stillMapped = leaveBlockMappingRepo.existsByLeaveBlockIdAndLeaveTypeId(blockId, ltId);
            if (!stillMapped) {
                leaveBlockLeaveTypeRepo.delete(blockLeaveType);
            }
        }

        // 5) if no mappings left, mark block inactive
        boolean anyMappingsLeft = leaveBlockMappingRepo.existsByLeaveBlockId(blockId);
        if (!anyMappingsLeft) {
            leaveBlock.setStatus(BlockStatus.INACTIVE);
            leaveBlock.setUpdatedAt(OffsetDateTime.now());
            leaveBlockRepo.save(leaveBlock);
        }

        System.out.println("Partial unblock processed for blockId=" + blockId);
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
    @Transactional
    public void deActivateLeaveBlock(String blockId) {
        // 1️⃣ Validate existence
        LeaveBlock block = leaveBlockRepo.findById(blockId)
                .orElseThrow(() -> new LeaveBlockException("Leave block not found with ID: " + blockId));

        if (block.getStatus() == BlockStatus.INACTIVE) {
            throw new LeaveBlockException("Leave block is already inactive.");
        }

        // 2️⃣ Unblock all affected employee leave balances
        List<LeaveBalance> balances = leaveBalanceRepo.findByBlockId(blockId);
        for (LeaveBalance balance : balances) {
            balance.setBlockId(null);
            balance.setIsBlocked(false);
        }
        if (!balances.isEmpty()) {
            leaveBalanceRepo.saveAll(balances);
        }

        // 3️⃣ Remove fine-grained mappings
        leaveBlockMappingRepo.deleteByLeaveBlockId(blockId);

        // 4️⃣ Remove higher-level associations (members & leave types)
        leaveBlockMemberRepo.deleteAllByLeaveBlock(block);
        leaveBlockLeaveTypeRepo.deleteAllByLeaveBlock(block);

        // 5️⃣ Update main block status
        block.setStatus(BlockStatus.INACTIVE);
        block.setUpdatedAt(OffsetDateTime.now());
        leaveBlockRepo.save(block);

        System.out.println("Leave block " + blockId + " successfully deactivated.");
    }

}
