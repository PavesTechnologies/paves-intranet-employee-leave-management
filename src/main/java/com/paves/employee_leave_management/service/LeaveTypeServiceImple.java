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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveTypeServiceImple implements LeaveTypeServiceInterface {

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

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
    @Override
    @Transactional
    public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType) {
        LeaveType savedLeaveType = leaveTypeRepo.save(leaveType);

    // ✅ Configure for all existing employees
        leaveBalanceServiceInterface.createLeaveBalanceForAllEmployees(savedLeaveType);

        return new ApiResponse<>(true, "LeaveType added successfully", savedLeaveType);
    }


    @Override
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        List<LeaveType> allLeaveTypes = leaveTypeRepo.findAll();
        if (allLeaveTypes.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(allLeaveTypes, HttpStatus.OK);
    }

//    @Override
//    public ResponseEntity<ApiResponse<LeaveType>> updateLeaveType(String leaveTypeId) {
//        return null;
//    }


    @Transactional
    @Override
    public ResponseEntity<LeaveType> updateLeaveType(LeaveType updatedLeaveType) {
        Optional<LeaveType> existingOpt = leaveTypeRepo.findByLeaveTypeId(updatedLeaveType.getLeaveTypeId());
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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

        return new ResponseEntity<>(savedLeaveType, HttpStatus.ACCEPTED);
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


}
