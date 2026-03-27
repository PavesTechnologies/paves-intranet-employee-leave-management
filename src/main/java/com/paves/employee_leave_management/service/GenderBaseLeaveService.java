package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GenderBaseLeaveService implements GenderBasedLeaveServiceInterface {

    @Autowired
    private GenderBasedRepo genderBasedRepo;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private GenderBasedLeaveBalancesRepo genderBasedLeaveBalancesRepo;

    @Autowired
    private EmailServiceInterface emailService;

    @Autowired
    private GenderBasedLeaveBalanceService genderBasedLeaveBalanceService;

    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ApiResponse<Object> createGenderBaseLeave(GenderBasedLeave genderBaseLeave) {
        genderBaseLeave.generateId();

        Optional<GenderBasedLeave> existing = genderBasedRepo.findById(genderBaseLeave.getLeaveTypeId());

        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActive())) {
            return new ApiResponse<>(false,
                    "Leave type already exists and is active",
                    null);
        }

        genderBaseLeave.setActive(Boolean.TRUE);
        genderBaseLeave.setCreatedAt(LocalDateTime.now());
        genderBaseLeave.setEffectiveEndDate(null);
        GenderBasedLeave saved = genderBasedRepo.save(genderBaseLeave);

        boolean shouldActivateNow = !saved.getEffectiveStartDate().isAfter(LocalDate.now());
        if (shouldActivateNow) {
            genderBasedLeaveBalanceService.createLeaveBalanceForAllEmployees(saved);
        }

        return new ApiResponse<>(
                true,
                shouldActivateNow
                        ? "Leave type created and effective immediately."
                        : "Leave type will become active on " + saved.getEffectiveStartDate(),
                saved
        );
    }

    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ApiResponse<Object> updateGenderBaseLeave(GenderBasedLeave genderBaseLeave, String leaveTypeId) {
        Optional<GenderBasedLeave> existing = genderBasedRepo.findById(leaveTypeId);
        if(existing.isEmpty()){
            return new ApiResponse<>(false,
                    "Leave type not found",
                    null);
        }

        GenderBasedLeave saved = genderBasedRepo.save(genderBaseLeave);
        return new ApiResponse<>(true,
                "Leave type updated successfully",
                saved);
    }

    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ApiResponse<Object> deActiveGenderBaseLeaveType(String leaveTypeId, LocalDate effectiveDate) {
        GenderBasedLeave existing = genderBasedRepo.findById(leaveTypeId).orElseThrow(()->new RuntimeException("Leave type not found"));
        if(effectiveDate.isAfter(LocalDate.now())){
            existing.setEffectiveEndDate(effectiveDate);
            return new ApiResponse<>(true,
                    "Leave type deactivated successfully",
                    existing);

        }else{
            existing.setActive(false);
            existing.setEffectiveEndDate(effectiveDate);
            genderBasedRepo.save(existing);
            genderBasedLeaveBalancesRepo.deleteByLeaveType_LeaveTypeId(leaveTypeId);
            return new ApiResponse<>(true,
                    "Leave type deactivated successfully",
                    existing);
        }
    }

    @Override
    public List<GenderBasedLeave> getAllLeaveTypes() {
        List<GenderBasedLeave> genderBasedLeaves = genderBasedRepo.findAll();
        Iterator<GenderBasedLeave> iterator = genderBasedLeaves.iterator();

        while (iterator.hasNext()) {
            GenderBasedLeave leave = iterator.next();
            if (!leave.getActive() || leave.getEffectiveStartDate().isAfter(LocalDate.now()) ) {
                iterator.remove(); // ✅ safe
            }
        }

       return genderBasedLeaves;
    }

//    @Override
//    public ApiResponse<Object> getAllLeaveTypes() {
//        List<GenderBasedLeave> genderBasedLeaves = genderBasedRepo.findAll().stream().filter(GenderBasedLeave::getActive).toList();
////        for (GenderBasedLeave leave: genderBasedLeaves){
////            if(!leave.getIsActive()){
////                genderBasedLeaves.remove(leave);
////            }
////        }
//        return new ApiResponse<>(true,
//                "Leave types fetched successfully",
//                genderBasedLeaves);
//    }


    @Override
    public Optional<GenderBasedLeave> getLeaveType(String leaveType) {
        return genderBasedRepo.findByLeaveTypeId(leaveType);
    }

}
