package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.BackgroundJob;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.event.LeaveBalanceCreationEvent;
import com.paves.employee_leave_management.repo.BackgroundJobRepository;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveTypeServiceImple implements LeaveTypeServiceInterface {


    private final LeaveTypeRepo leaveTypeRepo;

    private final LeaveBalanceRepo leaveBalanceRepo;

    private final LeaveBalanceServiceInterface leaveBalanceService;

    private final BackgroundJobRepository jobRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public LeaveTypeServiceImple(LeaveTypeRepo leaveTypeRepo, LeaveBalanceRepo leaveBalanceRepo,
            LeaveBalanceServiceInterface leaveBalanceService, BackgroundJobRepository jobRepo, ApplicationEventPublisher eventPublisher) {
        this.leaveTypeRepo = leaveTypeRepo;
        this.leaveBalanceRepo = leaveBalanceRepo;
        this.leaveBalanceService = leaveBalanceService;
        this.jobRepo = jobRepo;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ApiResponse<Object> addLeaveType(LeaveType leaveType) {
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
                System.out.println("From Add Leave Type");
                // Start the job and get the job object back
                BackgroundJob job = startLeaveBalanceCreationJob(savedLeaveType);

                return new ApiResponse<>(true,
                        "Leave type reactivated successfully. Balance creation is running in the background.",
                        job); // Return the job
            }
        }

        // Case 3: Creating a brand new leave type
        LeaveType savedLeaveType = leaveTypeRepo.save(leaveType);

        // Start the job and get the job object back
        BackgroundJob job = startLeaveBalanceCreationJob(savedLeaveType);

        return new ApiResponse<>(true,
                "Leave type created successfully. Balance creation is running in the background.",
                job); // Return the job
    }

    /**
     * Helper method to create the job record and trigger the async service.
     */
    private BackgroundJob startLeaveBalanceCreationJob(LeaveType leaveType) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType("CREATE_LEAVE_BALANCE");
        job.setStatus("PENDING");
        job.setProgress(0);
        job.setDetails("Creating balances for leaveType=" + leaveType.getLeaveName());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        // Save the job to get an ID before passing it to the async method
        BackgroundJob savedJob = jobRepo.save(job);
        System.out.println("From  startLeaveBalanceCreationJob");
        // Trigger the asynchronous process
        eventPublisher.publishEvent(new LeaveBalanceCreationEvent(this, leaveType, savedJob.getJobId()));
        System.out.println("From  startLeaveBalanceCreationJob 2");

        return savedJob;
    }


    private void updateLeaveTypeFields(LeaveType target, LeaveType source) {
        target.setActive(true);
        target.setLeaveName(source.getLeaveName());
        target.setDescription(source.getDescription());
        target.setAccrualRate(source.getAccrualRate());
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

    public BackgroundJob getJobStatus(String jobId) {
        return jobRepo.findById(jobId).orElseThrow();
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

}
