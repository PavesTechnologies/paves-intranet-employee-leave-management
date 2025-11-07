package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveBlockRequestDto;
import com.paves.employee_leave_management.dto.MappingUpdateDto;
import com.paves.employee_leave_management.dto.UnblockLeaveRequestDto;
import com.paves.employee_leave_management.dto.UpdateLeaveBlockRequest;
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
//    @Transactional

    /// /    public LeaveBlock updateLeaveBlock(UpdateLeaveBlockRequest requestDto) {
    /// /
    /// /        if(requestDto.getType() == "UNBLOCK"){
    /// /            UnblockLeaveRequestDto unblock = new UnblockLeaveRequestDto();
    /// /        }
    /// /
    /// /        LeaveBlock block = leaveBlockRepo.findById(requestDto.getBlockId())
    /// /                .orElseThrow(() -> new LeaveBlockException("LeaveBlock not found"));
    /// /
    /// /        // 1️⃣ Update main block details
    /// /        block.setReason(requestDto.getReason());
    /// /        block.setStartDate(requestDto.getStartDate());
    /// /        block.setEndDate(requestDto.getEndDate());
    /// /        block.setStatus(requestDto.getStatus());
    /// /        block.setUpdatedAt(OffsetDateTime.now());
    /// /
    /// /        leaveBlockRepo.save(block);
    /// /
    /// ///        if(requestDto.getMappingUpdates() == null || requestDto.getMappingUpdates().isEmpty() ){
    /// ///            return block;
    /// ///        }
    /// /
    /// /        // 2️⃣ Delete old mappings
    /// /        leaveBlockMappingRepo.deleteAllByLeaveBlock(block);
    /// /
    /// /        // 3️⃣ Insert updated mappings
    /// /        List<LeaveBlockMapping> mappings = requestDto.getMappingUpdates().stream()
    /// /                .map(update -> LeaveBlockMapping.builder()
    /// /                        .leaveBlock(block)
    /// /                        .employeeId(update.getEmployeeId())
    /// /                        .leaveTypeId(update.getLeaveTypeId())
    /// /                        .status(update.getStatus())
    /// /                        .year(block.getYear())
    /// /                        .build())
    /// /                .toList();
    /// /        leaveBlockMappingRepo.saveAll(mappings);
    /// /
    /// /        // 4️⃣ Reset old balances
    /// /        List<LeaveBalance> oldBalances = leaveBalanceRepo.findByBlockId(block.getId());
    /// /        oldBalances.forEach(b -> {
    /// /            b.setBlockId(null);
    /// /            b.setIsBlocked(false);
    /// /        });
    /// /        leaveBalanceRepo.saveAll(oldBalances);
    /// /
    /// /        // 5️⃣ Apply block only for ACTIVE statuses
    /// /        List<LeaveBalance> balancesToBlock = new ArrayList<>();
    /// /        for (LeaveBlockMapping m : mappings) {
    /// /            if (m.getStatus() == BlockStatus.ACTIVE) {
    /// /                LeaveBalance balance = leaveBalanceRepo
    /// /                        .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
    /// /                                m.getEmployeeId(), m.getLeaveTypeId(), m.getYear());
    /// /                if (balance != null) {
    /// /                    balance.setBlockId(block.getId());
    /// /                    balance.setIsBlocked(true);
    /// /                    balancesToBlock.add(balance);
    /// /                }
    /// /            }
    /// /        }
    /// /        leaveBalanceRepo.saveAll(balancesToBlock);
    /// /
    /// /        return block;
    /// /    }
//


//
//@Transactional
//public LeaveBlock updateLeaveBlock(UpdateLeaveBlockRequest requestDto) {
//
//    // ✅ Validate Block ID & Fetch
//    LeaveBlock block = leaveBlockRepo.findById(requestDto.getBlockId())
//            .orElseThrow(() -> new LeaveBlockException("LeaveBlock not found with ID: " + requestDto.getBlockId()));
//
//    // ✅ Update main block details
//    block.setReason(requestDto.getReason());
//    block.setStartDate(requestDto.getStartDate());
//    block.setEndDate(requestDto.getEndDate());
//    block.setStatus(requestDto.getStatus());
//    block.setUpdatedAt(OffsetDateTime.now());
//
//    leaveBlockRepo.save(block);
//
//    // ✅ If NO mapping updates, return after updating block info only
//    if (requestDto.getMappingUpdates() == null || requestDto.getMappingUpdates().isEmpty()) {
//        return block;
//    }
//
//    // ✅ 1. Delete old mappings
//    leaveBlockMappingRepo.deleteAllByLeaveBlock(block);
//
//    // ✅ 2. Insert updated mappings
//    List<LeaveBlockMapping> mappings = requestDto.getMappingUpdates().stream()
//            .map(update -> LeaveBlockMapping.builder()
//                    .leaveBlock(block)
//                    .employeeId(update.getEmployeeId())
//                    .leaveTypeId(update.getLeaveTypeId())
//                    .status(update.getStatus())
//                    .year(block.getYear())
//                    .build())
//            .toList();
//    leaveBlockMappingRepo.saveAll(mappings);
//
//    // ✅ 3. Reset (unblock) all balances previously blocked by this block
//    List<LeaveBalance> oldBalances = leaveBalanceRepo.findByBlockId(block.getId());
//    oldBalances.forEach(b -> {
//        b.setBlockId(null);
//        b.setIsBlocked(false);
//    });
//    leaveBalanceRepo.saveAll(oldBalances);
//
//    // ✅ 4. Apply blocking again only for ACTIVE mappings
//    List<LeaveBalance> balancesToBlock = new ArrayList<>();
//    for (LeaveBlockMapping m : mappings) {
//        if (m.getStatus() == BlockStatus.ACTIVE) {
//            LeaveBalance balance = leaveBalanceRepo
//                    .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
//                            m.getEmployeeId(), m.getLeaveTypeId(), m.getYear());
//
//            if (balance != null) {
//                balance.setBlockId(block.getId());
//                balance.setIsBlocked(true);
//                balancesToBlock.add(balance);
//            }
//        }
//    }
//
//    leaveBalanceRepo.saveAll(balancesToBlock);
//
//    return block;
//}
    @Transactional
    public LeaveBlock updateLeaveBlock(UpdateLeaveBlockRequest requestDto) {

        LeaveBlock block = leaveBlockRepo.findById(requestDto.getBlockId())
                .orElseThrow(() -> new LeaveBlockException("LeaveBlock not found with ID: " + requestDto.getBlockId()));

        // Determine type if not provided: auto-detect (C behavior)
        String type = requestDto.getType();
        if (type == null) {
            boolean hasUnblocks = requestDto.getUnblockedRequests() != null && !requestDto.getUnblockedRequests().isEmpty();
            boolean hasMappings = requestDto.getMappingUpdates() != null && !requestDto.getMappingUpdates().isEmpty();
            boolean hasUpdates = requestDto.getReason() != null || requestDto.getStartDate() != null || requestDto.getEndDate() != null || requestDto.getStatus() != null;
            if (hasUnblocks && !hasMappings && !hasUpdates) {
                type = "UNBLOCK";
            } else {
                type = "UPDATE";
            }
        }

        // UNBLOCK-only flow: do not touch mappings (unless you want to mark them inactive)
        if ("UNBLOCK".equalsIgnoreCase(type)) {
            handleUnblockRequests(block, requestDto.getUnblockedRequests(), requestDto.getYear());
            // after unblocking, return the current block (may have been set inactive inside handler)
            return block;
        }

        // UPDATE or mixed flow
        // 1) Update block meta (patch-style: only set non-null fields)
        updateBlockDetailsPatch(block, requestDto);

        // 2) Update mappings only if mappingUpdates provided
        if (requestDto.getMappingUpdates() != null) {
            updateMappingsReplace(block, requestDto.getMappingUpdates());
        }

        // 3) If mappings were replaced or block metadata changed in a way that impacts blocking,
        //    reapply block logic so balances reflect the current mappings.
        //    We'll reapply whenever mappingUpdates provided OR updates touched status/start/end/reason.
        boolean touchedMetadata = requestDto.getReason() != null
                || requestDto.getStartDate() != null
                || requestDto.getEndDate() != null
                || requestDto.getStatus() != null;

        if (requestDto.getMappingUpdates() != null || touchedMetadata) {
            reapplyBlockLogic(block, requestDto.getMappingUpdates());
        }

        return block;
    }


    private void updateBlockDetailsPatch(LeaveBlock block, UpdateLeaveBlockRequest dto) {
        // only set fields that are provided (PATCH semantics)
        if (dto.getUpdates().getReason() != null) {
            block.setReason(dto.getUpdates().getReason());
        }
        if (dto.getUpdates().getStartDate() != null) {
            block.setStartDate(dto.getUpdates().getStartDate());
        }
        if (dto.getUpdates().getEndDate() != null) {
            block.setEndDate(dto.getUpdates().getEndDate());
        }
        if (dto.getUpdates().getStatus() != null) {
            block.setStatus(dto.getUpdates().getStatus());
        }
        block.setUpdatedAt(OffsetDateTime.now());

        leaveBlockRepo.save(block);
    }


    private void updateMappingsReplace(LeaveBlock block, List<MappingUpdateDto> mappingUpdates) {
        if (mappingUpdates == null) return;

        // delete old mappings
//        leaveBlockMappingRepo.deleteAllByLeaveBlock(block);

        // if empty list was explicitly passed, that means "remove all mappings" - fine
        if (mappingUpdates.isEmpty()) {
            return;
        }


        // insert new mappings
//        List<LeaveBlockMapping> mappings = mappingUpdates.stream()
//                .map(update -> LeaveBlockMapping.builder()
//                        .leaveBlock(block)
//                        .employeeId(update.getEmployeeId())
//                        .leaveTypeId(update.getLeaveTypeId())
//                        .status(update.getStatus())
//                        .year(block.getYear())
//                        .build())
//                .toList();

//        leaveBlockMappingRepo.saveAll(mappings);

        for (MappingUpdateDto map : mappingUpdates) {
            LeaveBlockMapping data = leaveBlockMappingRepo.getByLeaveBlockIdAndEmployeeIdAndLeaveTypeId(block.getId(), map.getEmployeeId(), map.getLeaveTypeId());
            if (data != null) {
                data.setStatus(map.getStatus());
                leaveBlockMappingRepo.save(data);
            }
        }
    }


    private void reapplyBlockLogic(LeaveBlock block, List<MappingUpdateDto> mappingUpdates) {

        // 1. Reset balances that were previously blocked by this block
        List<LeaveBalance> oldBalances = leaveBalanceRepo.findByBlockId(block.getId());
        if (oldBalances != null && !oldBalances.isEmpty()) {
            oldBalances.forEach(b -> {
                b.setBlockId(null);
                b.setIsBlocked(false);
            });
            leaveBalanceRepo.saveAll(oldBalances);
        }

//         2. Apply block only for ACTIVE mappings
        List<LeaveBlockMapping> mappings = leaveBlockMappingRepo.findByLeaveBlock(block);
        if (mappings == null || mappings.isEmpty()) return;

        List<LeaveBalance> balancesToBlock = new ArrayList<>();
        for (LeaveBlockMapping m : mappings) {
            if (m.getStatus() == BlockStatus.ACTIVE) {
                LeaveBalance balance = leaveBalanceRepo
                        .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                                m.getEmployeeId(), m.getLeaveTypeId(), m.getYear());

                if (balance != null) {
                    balance.setBlockId(block.getId());
                    balance.setIsBlocked(true);
                    balancesToBlock.add(balance);
                }
            }
        }
        if (!balancesToBlock.isEmpty()) {
            leaveBalanceRepo.saveAll(balancesToBlock);
        }


    }


    private void handleUnblockRequests(LeaveBlock block, List<UpdateLeaveBlockRequest.EmployeeUnblockRequest> requests, Integer year) {
        if (requests == null || requests.isEmpty()) return;

        String blockId = block.getId();

        for (UpdateLeaveBlockRequest.EmployeeUnblockRequest req : requests) {
            String employeeId = req.getEmployeeId();
            List<String> leaveTypeIdsToUnblock = req.getLeaveTypeIds();

            if (leaveTypeIdsToUnblock == null || leaveTypeIdsToUnblock.isEmpty()) continue;

            // 1) bulk update leave balances (your repo helper)
            int balancesUpdated = leaveBalanceRepo.unblockBalancesForEmployeeAndTypes(employeeId, blockId, leaveTypeIdsToUnblock);
            // optional: log
//            logger.debug("Balances updated (unblock) for employee {}: {}", employeeId, balancesUpdated);

            // 2) mark mappings inactive (bulk)
            int mappingsUpdated = leaveBlockMappingRepo.markMappingsInactive(BlockStatus.INACTIVE, blockId, employeeId, leaveTypeIdsToUnblock);
//            logger.debug("Mappings set inactive for employee {}: {}", employeeId, mappingsUpdated);

            // 3) if no mappings left for this employee, remove the member entry
            boolean hasMappingsForEmployee = leaveBlockMappingRepo.existsByLeaveBlockIdAndEmployeeId(blockId, employeeId);
            if (!hasMappingsForEmployee) {
                leaveBlockMemberRepo.deleteByLeaveBlockIdAndEmployeeId(blockId, employeeId);
            }
        }

        // 4) remove leave types from block if no mapping exists for that leaveType anymore
        List<LeaveBlockLeaveType> blockLeaveTypes = leaveBlockLeaveTypeRepo.findByLeaveBlock(block);
        if (blockLeaveTypes != null && !blockLeaveTypes.isEmpty()) {
            for (LeaveBlockLeaveType blockLeaveType : blockLeaveTypes) {
                String ltId = blockLeaveType.getLeaveTypeId();
                boolean stillMapped = leaveBlockMappingRepo.existsByLeaveBlockIdAndLeaveTypeId(blockId, ltId);
                if (!stillMapped) {
                    leaveBlockLeaveTypeRepo.delete(blockLeaveType);
                }
            }
        }

        // 5) if no mappings left at all for this block, mark it INACTIVE
        boolean anyMappingsLeft = leaveBlockMappingRepo.existsByLeaveBlockId(blockId);
        if (!anyMappingsLeft) {
            block.setStatus(BlockStatus.INACTIVE);
            block.setUpdatedAt(OffsetDateTime.now());
            leaveBlockRepo.save(block);
        }
    }


    private void updateBlockDetails(LeaveBlock block, UpdateLeaveBlockRequest dto) {
        block.setReason(dto.getReason());
        block.setStartDate(dto.getStartDate());
        block.setEndDate(dto.getEndDate());
        block.setStatus(dto.getStatus());
        block.setUpdatedAt(OffsetDateTime.now());

        leaveBlockRepo.save(block);
    }

    private void updateMappings(LeaveBlock block, List<MappingUpdateDto> mappingUpdates) {

        // Delete old mappings
        leaveBlockMappingRepo.deleteAllByLeaveBlock(block);

        // Insert new mappings
        List<LeaveBlockMapping> mappings = mappingUpdates.stream()
                .map(update -> LeaveBlockMapping.builder()
                        .leaveBlock(block)
                        .employeeId(update.getEmployeeId())
                        .leaveTypeId(update.getLeaveTypeId())
                        .status(update.getStatus())
                        .year(block.getYear())
                        .build())
                .toList();

        leaveBlockMappingRepo.saveAll(mappings);
    }


    //
//
//
//    // ------------------------------------------------------
//    // UNBLOCK
//    // ------------------------------------------------------
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
