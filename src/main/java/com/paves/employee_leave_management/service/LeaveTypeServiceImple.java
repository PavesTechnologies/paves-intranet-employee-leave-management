package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveTypeServiceImple implements LeaveTypeServiceInterface {

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

//    @Autowired
//    LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceServiceInterface;

//    @Override
//    @Transactional
//    public ResponseEntity<LeaveType> addLeaveType(LeaveType leaveType) {
//        Optional<LeaveType> leaveRes = leaveTypeRepo.findByLeaveTypeId(leaveType.getLeaveTypeId());
//        if(leaveRes.isEmpty()){
//            return new ResponseEntity<>(leaveTypeRepo.save(leaveType), HttpStatus.OK);
//            leaveBalanceService.createLeaveBalanceForAllEmployees(savedLeaveType);
//        }
//        return new ResponseEntity<>(leaveTypeRepo.save(leaveType), HttpStatus.OK);
//    }
//    @Override
//    @Transactional
//    public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType) {
//        leaveType.generateId(); // ensure leaveTypeId is set
//
//        Optional<LeaveType> existingLeaveType = leaveTypeRepo.findById(leaveType.getLeaveTypeId());
//
//        if (existingLeaveType.isPresent()) {
//            LeaveType dbLeaveType = existingLeaveType.get();
//
//            if (Boolean.TRUE.equals(dbLeaveType.getActive())) {
//                return new ApiResponse<>(false,
//                        "Leave type " + leaveType.getLeaveTypeId() + " already exists and is active.",
//                        null);
//            } else {
//                // Reactivate
//                dbLeaveType.setActive(true);
//                dbLeaveType.setLeaveName(leaveType.getLeaveName());
//                dbLeaveType.setDescription(leaveType.getDescription());
//                double accrualRate = ((double)leaveType.getMaxDaysPerYear()/12);
//                dbLeaveType.setAccrualRate(accrualRate);
//                dbLeaveType.setAccrualFrequency(leaveType.getAccrualFrequency());
//                dbLeaveType.setAdvanceNoticeDays(leaveType.getAdvanceNoticeDays());
//                dbLeaveType.setWeekendsAndHolidaysAllowed(leaveType.getWeekendsAndHolidaysAllowed());
//                dbLeaveType.setAllowHalfDay(leaveType.getAllowHalfDay());
//                dbLeaveType.setMaxCarryForward(leaveType.getMaxCarryForward());
//                dbLeaveType.setMaxCarryForwardPerYear(leaveType.getMaxCarryForwardPerYear());
//                dbLeaveType.setNoticePeriodRestriction(leaveType.getNoticePeriodRestriction());
//                dbLeaveType.setPastDateLimitDays(leaveType.getPastDateLimitDays());
//                dbLeaveType.setRequiresDocumentation(leaveType.getRequiresDocumentation());
//                dbLeaveType.setWaitingPeriodDays(leaveType.getWaitingPeriodDays());
//                dbLeaveType.setAllowNegativeBalance(leaveType.getAllowNegativeBalance());
//                dbLeaveType.setExpiryDays(leaveType.getExpiryDays());
//                dbLeaveType.setMaxDaysPerYear(leaveType.getMaxDaysPerYear());
//
//                LeaveType reactivated = leaveTypeRepo.save(dbLeaveType);
//                leaveBalanceService.createLeaveBalanceForAllEmployees(reactivated);
//
//                return new ApiResponse<>(true,
//                        "Leave type reactivated successfully.",
//                        reactivated);
//            }
//        }
//
//        // Create new
//        double accrualRate = ((double)leaveType.getMaxDaysPerYear()/12.0);
//        leaveType.setAccrualRate(accrualRate);
//        LeaveType savedLeaveType = leaveTypeRepo.save(leaveType);
//        leaveBalanceService.createLeaveBalanceForAllEmployees(savedLeaveType);
//        return new ApiResponse<>(true,
//                "Leave type created successfully.",
//                savedLeaveType);
//    }
@Override
@Transactional
public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType) {
    leaveType.generateId(); // ensure leaveTypeId is set

    Optional<LeaveType> existingLeaveType = leaveTypeRepo.findById(leaveType.getLeaveTypeId());

    if (existingLeaveType.isPresent()) {
        LeaveType dbLeaveType = existingLeaveType.get();

        if (Boolean.TRUE.equals(dbLeaveType.getActive())) {
            // Case 1: Already exists and is active
            return new ApiResponse<>(false,
                    "Leave type " + leaveType.getLeaveTypeId() + " already exists and is active.",
                    null);
        } else {
            // Case 2: Reactivating an existing leave type
            updateLeaveTypeFields(dbLeaveType, leaveType);
            LeaveType savedLeaveType = leaveTypeRepo.save(dbLeaveType);

            // Directly create leave balances (no background job)
            leaveBalanceServiceInterface.createLeaveBalanceForAllEmployees(savedLeaveType);

            return new ApiResponse<>(true,
                    "Leave type reactivated successfully. Leave balances created for employees.",
                    savedLeaveType);
        }
    }

    // Case 3: Creating a brand new leave type
    LeaveType savedLeaveType = leaveTypeRepo.save(leaveType);

    // Directly create leave balances (no background job)
    leaveBalanceServiceInterface.createLeaveBalanceForAllEmployees(savedLeaveType);

    return new ApiResponse<>(true,
            "Leave type created successfully. Leave balances created for employees.",
            savedLeaveType);
}

    private void updateLeaveTypeFields(LeaveType target, LeaveType source) {
        target.setActive(true);
        target.setLeaveName(source.getLeaveName());
        target.setDescription(source.getDescription());
        double accrualRate = ((double)source.getMaxDaysPerYear()/12.0);
        source.setAccrualRate(accrualRate);
        target.setAccrualFrequency(source.getAccrualFrequency());
        target.setAdvanceNoticeDays(source.getAdvanceNoticeDays());
        target.setWeekendsAndHolidaysAllowed(source.getWeekendsAndHolidaysAllowed());
        target.setAllowHalfDay(source.getAllowHalfDay());
        target.setMaxCarryForward(source.getMaxCarryForward());
        target.setMaxCarryForwardPerYear(source.getMaxCarryForwardPerYear());
        target.setNoticePeriodRestriction(source.getNoticePeriodRestriction());
        target.setPastDateLimitDays(source.getPastDateLimitDays());
        target.setRequiresDocumentation(source.getRequiresDocumentation());
        target.setWaitingPeriodDays(source.getWaitingPeriodDays());
        target.setAllowNegativeBalance(source.getAllowNegativeBalance());
        target.setExpiryDays(source.getExpiryDays());
        target.setMaxDaysPerYear(source.getMaxDaysPerYear());
    }




    @Override
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        List<LeaveType> allLeaveTypes = leaveTypeRepo.findAll();
        if (allLeaveTypes.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        List<LeaveType> activeLeaveTypes = leaveTypeRepo.findByActiveTrue();

        return new ResponseEntity<>(activeLeaveTypes, HttpStatus.OK);
    }

//    @Override
//    public ResponseEntity<ApiResponse<LeaveType>> updateLeaveType(String leaveTypeId) {
//        return null;
//    }


    @Transactional
    @Override
    public ApiResponse<LeaveType> updateLeaveType(LeaveType updatedLeaveType, String leaveTypeId) {
        Optional<LeaveType> existingOpt = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);
        if (existingOpt.isEmpty()) {
            return new ApiResponse<>(false,
                    "Leave type " + leaveTypeId + " not found.",
                    null);
        }
//        LeaveType existingLeaveType = existingOpt.get();
        double newAccrualRate = updatedLeaveType.getAccrualRate();
        // Save updated LeaveType
        LeaveType savedLeaveType = leaveTypeRepo.save(updatedLeaveType);

        // Get remaining months in the year (excluding current month)
        int currentMonth = LocalDate.now().getMonthValue(); // 1 to 12
        int remainingMonths = 12 - currentMonth;

        System.out.println("Remaining months: Swarna here");

        // Get all LeaveBalance entries for this leave type
        List<LeaveBalance> affectedBalances = leaveBalanceRepo.findByLeaveType(savedLeaveType);

        for (LeaveBalance balance : affectedBalances) {
            double accruedLeaves = balance.getAccruedLeaves(); // leaves_till_now
            double recalculatedTotal = accruedLeaves + (remainingMonths * newAccrualRate);


            balance.setTotalLeaves(recalculatedTotal);

            // Optional: update availableLeaves if needed
            // double usedLeaves = balance.getUsedLeaves();
            // balance.setAvailableLeaves(recalculatedTotal - usedLeaves);
        }

        leaveBalanceRepo.saveAll(affectedBalances);

        return new ApiResponse<>(true,
                "Leave type updated successfully.",
                savedLeaveType);
    }


    @Override
    public ResponseEntity<LeaveType> getLeaveTypeById(String leaveTypeId) {
        Optional<LeaveType> optionalLeaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);

        return optionalLeaveType.map(leaveType -> new ResponseEntity<>(leaveType, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @Override
    public ResponseEntity<String> deleteLeaveType(String leaveTypeId) {
        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                .orElseThrow(()-> new RuntimeException("Leave type not found."));
        List<LeaveBalance> leaveBalanceList = leaveBalanceRepo.findByLeaveType(leaveType);
        leaveTypeRepo.delete(leaveType);
        return new ResponseEntity<>("Leave type deleted successfully", HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<String> deActiveLeaveType(String leaveTypeId) {
        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId).orElseThrow(
                ()->new RuntimeException("Leave Type Not Found"));

        leaveType.setActive(false);
        leaveTypeRepo.save(leaveType);

        leaveBalanceRepo.deleteByLeaveType(leaveType);
        return new ResponseEntity<>("Leave type deactivated successfully", HttpStatus.OK);
    }

}
