package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveTypeException;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceServiceInterface;

    @Override
    @Transactional
    public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType) {
        leaveType.generateId(); // ensure leaveTypeId is set

        Optional<LeaveType> existingLeaveType = leaveTypeRepo.findById(leaveType.getLeaveTypeId());

        if (existingLeaveType.isPresent()) {
            LeaveType dbLeaveType = existingLeaveType.get();

            if (Boolean.TRUE.equals(dbLeaveType.getActive())) {
                return new ApiResponse<>(false,
                        "Leave type " + leaveType.getLeaveTypeId() + " already exists and is active.",
                        null);
            } else {
                // Reactivate
                dbLeaveType.setActive(true);
                dbLeaveType.setLeaveName(leaveType.getLeaveName());
                dbLeaveType.setDescription(leaveType.getDescription());
                dbLeaveType.setAccrualRate(leaveType.getAccrualRate());
                dbLeaveType.setAccrualFrequency(leaveType.getAccrualFrequency());
                dbLeaveType.setAdvanceNoticeDays(leaveType.getAdvanceNoticeDays());
                dbLeaveType.setWeekendsAndHolidaysAllowed(leaveType.getWeekendsAndHolidaysAllowed());
                dbLeaveType.setAllowHalfDay(leaveType.getAllowHalfDay());
                dbLeaveType.setMaxCarryForward(leaveType.getMaxCarryForward());
                dbLeaveType.setMaxCarryForwardPerYear(leaveType.getMaxCarryForwardPerYear());
                dbLeaveType.setNoticePeriodRestriction(leaveType.getNoticePeriodRestriction());
                dbLeaveType.setPastDateLimitDays(leaveType.getPastDateLimitDays());
                dbLeaveType.setRequiresDocumentation(leaveType.getRequiresDocumentation());
                dbLeaveType.setWaitingPeriodDays(leaveType.getWaitingPeriodDays());
                dbLeaveType.setAllowNegativeBalance(leaveType.getAllowNegativeBalance());
                dbLeaveType.setExpiryDays(leaveType.getExpiryDays());
                dbLeaveType.setMaxDaysPerYear(leaveType.getMaxDaysPerYear());
                dbLeaveType.setPolicyDocument(leaveType.getPolicyDocument());

                LeaveType reactivated = leaveTypeRepo.save(dbLeaveType);
                leaveBalanceService.createLeaveBalanceForAllEmployees(reactivated);

                return new ApiResponse<>(true,
                        "Leave type reactivated successfully.",
                        reactivated);
            }
        }

        // Create new
        LeaveType savedLeaveType = leaveTypeRepo.save(leaveType);
        leaveBalanceService.createLeaveBalanceForAllEmployees(savedLeaveType);
        return new ApiResponse<>(true,
                "Leave type created successfully.",
                savedLeaveType);
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

    @Transactional
    public ResponseEntity<String> deActiveLeaveType(String leaveTypeId) {
        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId).orElseThrow(
                ()->new RuntimeException("Leave Type Not Found"));

        leaveType.setActive(false);
        leaveTypeRepo.save(leaveType);

        leaveBalanceRepo.deleteByLeaveType(leaveType);
        return new ResponseEntity<>("Leave type deactivated successfully", HttpStatus.OK);
    }

    @Override
    public void uploadDocument(String leaveTypeId, MultipartFile file) throws Exception {
        LeaveType leaveType = leaveTypeRepo.findById(leaveTypeId)
                .orElseThrow(() -> new LeaveTypeException("Leave type not found"));

        // Only accept PDF or Word files
        String contentType = file.getContentType();
        if (!contentType.equalsIgnoreCase("application/pdf") &&
                !contentType.equalsIgnoreCase("application/msword") &&
                !contentType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            throw new RuntimeException("Only PDF and Word documents are allowed");
        }

        leaveType.setPolicyDocument(file.getBytes());
        leaveTypeRepo.save(leaveType);
    }

    @Override
    public byte[] viewDocument(String leaveTypeName, String fileType) throws Exception {
        LeaveType leaveType = leaveTypeRepo.findByLeaveName(leaveTypeName)
                .orElseThrow(() -> new LeaveTypeException("Leave type not found"));

        if (leaveType.getPolicyDocument() == null) {
            throw new LeaveTypeException("No document uploaded for this leave type");
        }

        return leaveType.getPolicyDocument();
    }

    @Override
    public void deleteDocument(String leaveTypeId) throws Exception {
        LeaveType leaveType = leaveTypeRepo.findById(leaveTypeId)
                .orElseThrow(() -> new LeaveTypeException("Leave type not found"));

        leaveType.setPolicyDocument(null);
        leaveTypeRepo.save(leaveType);
    }

    // Helper to get MIME type from extension
    @Override
    public String getMimeType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
