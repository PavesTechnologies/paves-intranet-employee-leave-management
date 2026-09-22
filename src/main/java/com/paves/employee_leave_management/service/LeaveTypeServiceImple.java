package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.enums.LeaveStatusCompoff;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.globalExceptionHandler.ApprovalBusinessException;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveTypeException;
import com.paves.employee_leave_management.repo.*;
import com.paves.employee_leave_management.serviceInterface.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class LeaveTypeServiceImple implements LeaveTypeServiceInterface {

    private final LeaveTypeRepo leaveTypeRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final GenderBasedRepo genderBasedRepo;
    private final GenderBasedLeaveServiceInterface genderBasedLeaveServiceInterface;
    private final LeaveRequestRepo leaveRequestRepo;
    private final LeaveCompoffRepo leaveCompoffRepo;
    private final EmailServiceInterface emailService;
    private final EmployeeRepo employeeRepo;
    private final LeaveBalanceJobServiceInterface leaveBalanceJobService;
    private final LeaveBalanceJobRepository jobRepository;
    private final AsyncNotificationServiceInterface asyncNotificationService;
    private final ScheduledLeaveTypeUpdateRepo scheduledLeaveTypeUpdateRepo;
    private final ObjectMapper objectMapper;

    @Autowired @Lazy
    private LeaveTypeServiceInterface self;

    public LeaveTypeServiceImple(
            LeaveTypeRepo leaveTypeRepo,
            LeaveBalanceRepo leaveBalanceRepo,
            GenderBasedRepo genderBasedRepo,
            GenderBasedLeaveServiceInterface genderBasedLeaveServiceInterface,
            LeaveRequestRepo leaveRequestRepo,
            LeaveCompoffRepo leaveCompoffRepo,
            EmailServiceInterface emailService,
            EmployeeRepo employeeRepo,
            LeaveBalanceJobServiceInterface leaveBalanceJobService,
            LeaveBalanceJobRepository jobRepository,
            AsyncNotificationServiceInterface asyncNotificationService,
            ScheduledLeaveTypeUpdateRepo scheduledLeaveTypeUpdateRepo,
            ObjectMapper objectMapper
    ) {
        this.leaveTypeRepo = leaveTypeRepo;
        this.leaveBalanceRepo = leaveBalanceRepo;
        this.genderBasedRepo = genderBasedRepo;
        this.genderBasedLeaveServiceInterface = genderBasedLeaveServiceInterface;
        this.leaveRequestRepo = leaveRequestRepo;
        this.leaveCompoffRepo = leaveCompoffRepo;
        this.emailService = emailService;
        this.employeeRepo = employeeRepo;
        this.leaveBalanceJobService = leaveBalanceJobService;
        this.jobRepository = jobRepository;
        this.asyncNotificationService = asyncNotificationService;
        this.scheduledLeaveTypeUpdateRepo = scheduledLeaveTypeUpdateRepo;
        this.objectMapper = objectMapper;
    }



    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
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
                double newAccrualRate = 0;
                if (leaveType.getMaxDaysPerYear() == null) {
                    newAccrualRate = 0;
                } else {
                    newAccrualRate = (double) leaveType.getMaxDaysPerYear() / 12;
                    newAccrualRate = Math.round(newAccrualRate * 100.0) / 100.0;
                }

                boolean shouldActiveNow = !leaveType.getEffectiveStartDate().isAfter(LocalDate.now());
                dbLeaveType.setActive(shouldActiveNow);
                dbLeaveType.setLeaveName(leaveType.getLeaveName());
                dbLeaveType.setDescription(leaveType.getDescription());
                dbLeaveType.setAccrualRate(newAccrualRate);
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
//                dbLeaveType.setPolicyDocument(leaveType.getPolicyDocument());
                dbLeaveType.setCreateAt(LocalDateTime.now());
                dbLeaveType.setEffectiveStartDate(leaveType.getEffectiveStartDate());
                dbLeaveType.setDeactivationEffectiveDate(null);

                dbLeaveType.setLastUpdatedAt(null);
                LeaveType reactivated = leaveTypeRepo.save(dbLeaveType);

                if (shouldActiveNow) {
                    String jobId = startLeaveBalanceJob(reactivated, "SYSTEM");
                    reactivated.setJobId(jobId);
                    reactivated = leaveTypeRepo.save(reactivated);
                }
                return new ApiResponse<>(true,
                        dbLeaveType.getActive()
                                ? "Leave type reactivated and effective immediately."
                                : "Leave type reactivated and will be effective from "
                                + leaveType.getEffectiveStartDate(),
                        reactivated);
            }
        }

        // Create new
        double newAccrualRate = 0;
        if (leaveType.getMaxDaysPerYear() == null) {
            newAccrualRate = 0;
        } else {
            newAccrualRate = (double) leaveType.getMaxDaysPerYear() / 12;
            newAccrualRate = Math.round(newAccrualRate * 100.0) / 100.0;
        }

        boolean shouldActivateNow = !leaveType.getEffectiveStartDate().isAfter(LocalDate.now());
        leaveType.setActive(shouldActivateNow);
        leaveType.setAccrualRate(newAccrualRate);
        leaveType.setCreateAt(LocalDateTime.now());
        LeaveType savedLeaveType = leaveTypeRepo.save(leaveType);

        String jobId;
        if (shouldActivateNow) {
//            leaveBalanceService.createLeaveBalanceForAllEmployees(savedLeaveType);
            jobId = startLeaveBalanceJob(savedLeaveType, "SYSTEM");
            savedLeaveType.setJobId(jobId);
            leaveTypeRepo.save(savedLeaveType);
            log.info("Started Leave Balance job {} for Leave Type {}", jobId, savedLeaveType.getLeaveName());
        }

        // Notify all employees
        notifyAllEmployees(
                "New Leave Policy: " + savedLeaveType.getLeaveName(),
                "leave-policy-creation-notification.html",
                Map.of("leavePolicyName", savedLeaveType.getLeaveName())
        );

        return new ApiResponse<>(true,
                savedLeaveType.getActive()
                        ? "Leave type created and effective immediately."
                        : "Leave type created and will become active on "
                        + leaveType.getEffectiveStartDate(),
                savedLeaveType);
    }

    @Override
    public String startLeaveBalanceJob(LeaveType leaveType, String createdBy) {
        LeaveBalanceJob job = LeaveBalanceJob.builder()
                .leaveTypeId(leaveType.getLeaveTypeId())
                .leaveTypeName(leaveType.getLeaveName())
                .status(LeaveBalanceJob.JobStatus.PENDING)
                .createdBy(createdBy)
                .build();

        LeaveBalanceJob saved = jobRepository.save(job);
        leaveBalanceJobService.processLeaveBalancesAsync(saved.getJobId(), leaveType.getLeaveTypeId());
        return saved.getJobId();
    }


    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ResponseEntity<ApiResponse<Object>> createDirectly(LeaveType leaveType, AdminMaker maker) {
        log.info("Super admin {} creating leave type directly: {}",
                maker.getEmployeeId(), leaveType.getLeaveName());

        // delegate entirely to existing business logic
        ApiResponse<LeaveType> result = self.addLeaveType(leaveType);

        if (!result.isSuccess()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(false, result.getMessage(), null));
        }

        log.info("Leave type created directly by super admin: {} result: {}",
                maker.getEmployeeId(), result.getMessage());

        return ResponseEntity.ok(new ApiResponse<>(true, result.getMessage(), result.getData()));
    }



    @Override
//    @Cacheable("leave-types")
    public List<Map<String, String>> getLeaveTypes() {
        return Arrays.stream(LeaveTypesEnum.values())
                .map(type -> Map.of(
                        "name", type.name(),
                        "label", type.getLabel()
                ))
                .toList();
    }


    @Override
    @Cacheable("all-leave-types")
    public AllLeaveTypesListResponseDTO getAllLeaveTypes() {
//        List<LeaveType> allLeaveTypes = leaveTypeRepo.findAll();
//        if (allLeaveTypes.isEmpty()) {
//            return null;
//        }
        List<GenderBasedLeave> genderBasedLeaves = genderBasedLeaveServiceInterface.getAllLeaveTypes();
        List<LeaveType> activeLeaveTypes = leaveTypeRepo.findByActiveTrue();

        AllLeaveTypesListResponseDTO leaveTypeDTO = new AllLeaveTypesListResponseDTO();
        leaveTypeDTO.setRegular(activeLeaveTypes);
        leaveTypeDTO.setGenderBasedLeaves(genderBasedLeaves);

        return leaveTypeDTO;
    }



    @Transactional
    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true)
            }
    )
    public ApiResponse<LeaveType> updateLeaveType(LeaveType updatedLeaveType, String leaveTypeId) {
        Optional<LeaveType> existingOpt = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);
        if (existingOpt.isEmpty()) {
            return new ApiResponse<>(false,
                    "Leave type " + leaveTypeId + " not found.",
                    null);
        }

        LocalDate effectiveDate = updatedLeaveType.getEffectiveStartDate();
        boolean isFutureDated = effectiveDate != null && effectiveDate.isAfter(LocalDate.now());

        if (isFutureDated) {
            Optional<ScheduledLeaveTypeUpdate> existingPending =
                    scheduledLeaveTypeUpdateRepo.findByLeaveTypeIdAndStatus(
                            leaveTypeId, ScheduledLeaveTypeUpdate.Status.PENDING);

            if (existingPending.isPresent()) {
                ScheduledLeaveTypeUpdate pending = existingPending.get();
                return new ApiResponse<>(false,
                        "A scheduled update for this leave type is already pending, effective "
                                + pending.getEffectiveDate() + " (schedule id: " + pending.getId()
                                + "). Cancel it before scheduling another update.",
                        null);
            }

            String payload;
            try {
                payload = objectMapper.writeValueAsString(updatedLeaveType);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing scheduled leave type update", e);
            }

            ScheduledLeaveTypeUpdate scheduled = ScheduledLeaveTypeUpdate.builder()
                    .leaveTypeId(leaveTypeId)
                    .effectiveDate(effectiveDate)
                    .payload(payload)
                    .status(ScheduledLeaveTypeUpdate.Status.PENDING)
                    .build();
            scheduledLeaveTypeUpdateRepo.save(scheduled);

            log.info("Scheduled update {} for leave type {} effective {}",
                    scheduled.getId(), leaveTypeId, effectiveDate);

            return new ApiResponse<>(true,
                    "Update scheduled to take effect on " + effectiveDate + ".",
                    null);
        }

        LeaveType savedLeaveType = applyLeaveTypeUpdate(updatedLeaveType, existingOpt.get());

        return new ApiResponse<>(true,
                "Leave type updated successfully.",
                savedLeaveType);
    }

    // Applies the incoming field values onto the persisted leave type, recomputes accrual rate,
    // recalculates this year's non-deleted balances, and notifies employees. Shared by the
    // immediate-update path above and applyScheduledUpdate() below, so there's exactly one
    // place where "an update happens" — the nightly job doesn't reimplement any of this.
    private LeaveType applyLeaveTypeUpdate(LeaveType updatedLeaveType, LeaveType existing) {
        double newAccrualRate;
        if (updatedLeaveType.getMaxDaysPerYear() == null || updatedLeaveType.getMaxDaysPerYear() == 0) {
            newAccrualRate = 0;
        } else {
            newAccrualRate = (double) updatedLeaveType.getMaxDaysPerYear() / 12;
            newAccrualRate = new BigDecimal(newAccrualRate)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        updatedLeaveType.setAccrualRate(newAccrualRate);

        updatedLeaveType.setLastUpdatedAt(LocalDateTime.now());
        updatedLeaveType.setCreateAt(existing.getCreateAt());
        updatedLeaveType.setLeaveTypeId(existing.getLeaveTypeId());
        updatedLeaveType.setActive(existing.getActive());
        updatedLeaveType.setJobId(existing.getJobId());
        LeaveType savedLeaveType = leaveTypeRepo.save(updatedLeaveType);

        // Get remaining months in the year (excluding current month)
        int currentMonth = LocalDate.now().getMonthValue(); // 1 to 12
        int remainingMonths = 12 - currentMonth;
        int currentYear = LocalDate.now().getYear();

        // Scoped to this year and non-deleted rows only — a prior version of this recalculated
        // every year on record for this leave type, corrupting closed-year history.
        List<LeaveBalance> affectedBalances =
                leaveBalanceRepo.findByLeaveTypeAndYearNotDeleted(savedLeaveType, currentYear);

        for (LeaveBalance balance : affectedBalances) {
            double accruedLeaves = balance.getAccruedLeaves(); // leaves_till_now
            double recalculatedTotal = accruedLeaves + (remainingMonths * newAccrualRate);

            balance.setTotalLeaves(recalculatedTotal);

            double usedLeaves = balance.getUsedLeaves();
            balance.setRemainingLeaves((balance.getCarriedForward() + accruedLeaves) - usedLeaves);
        }

        leaveBalanceRepo.saveAll(affectedBalances);

        // Notify all employees
        notifyAllEmployees(
                "Leave Policy Updated: " + savedLeaveType.getLeaveName(),
                "leave-policy-update-notification.html",
                Map.of("leavePolicyName", savedLeaveType.getLeaveName())
        );

        return savedLeaveType;
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Override
    public void applyScheduledUpdate(ScheduledLeaveTypeUpdate scheduled) {
        try {
            LeaveType incoming = objectMapper.readValue(scheduled.getPayload(), LeaveType.class);
            LeaveType existing = leaveTypeRepo.findByLeaveTypeId(scheduled.getLeaveTypeId())
                    .orElseThrow(() -> new LeaveTypeException(
                            "Leave type not found: " + scheduled.getLeaveTypeId()));

            applyLeaveTypeUpdate(incoming, existing);

            scheduled.setStatus(ScheduledLeaveTypeUpdate.Status.APPLIED);
            scheduled.setAppliedAt(LocalDateTime.now());
            scheduledLeaveTypeUpdateRepo.save(scheduled);

            log.info("Applied scheduled update {} for leave type {}",
                    scheduled.getId(), scheduled.getLeaveTypeId());
        } catch (Exception e) {
            log.error("Failed to apply scheduled update {} for leave type {}: {}",
                    scheduled.getId(), scheduled.getLeaveTypeId(), e.getMessage(), e);
            scheduled.setStatus(ScheduledLeaveTypeUpdate.Status.FAILED);
            scheduled.setErrorMessage(e.getMessage());
            scheduled.setAppliedAt(LocalDateTime.now());
            scheduledLeaveTypeUpdateRepo.save(scheduled);
        }
    }

    @Override
    public ApiResponse<Object> cancelScheduledUpdate(String scheduleId) {
        Optional<ScheduledLeaveTypeUpdate> scheduledOpt = scheduledLeaveTypeUpdateRepo.findById(scheduleId);
        if (scheduledOpt.isEmpty()) {
            return new ApiResponse<>(false, "Scheduled update " + scheduleId + " not found.", null);
        }

        ScheduledLeaveTypeUpdate scheduled = scheduledOpt.get();
        if (scheduled.getStatus() != ScheduledLeaveTypeUpdate.Status.PENDING) {
            return new ApiResponse<>(false,
                    "Scheduled update " + scheduleId + " is " + scheduled.getStatus()
                            + " and can no longer be cancelled.",
                    null);
        }

        scheduled.setStatus(ScheduledLeaveTypeUpdate.Status.CANCELLED);
        scheduledLeaveTypeUpdateRepo.save(scheduled);

        return new ApiResponse<>(true, "Scheduled update cancelled.", null);
    }

    @Override
    public ScheduledLeaveTypeUpdate getScheduledUpdateForLeaveType(String leaveTypeId) {
        return scheduledLeaveTypeUpdateRepo
                .findByLeaveTypeIdAndStatus(leaveTypeId, ScheduledLeaveTypeUpdate.Status.PENDING)
                .orElse(null);
    }

    @Override
    public List<ScheduledLeaveTypeUpdate> getAllScheduledUpdates(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return scheduledLeaveTypeUpdateRepo.findAllByOrderByCreatedAtDesc();
        }
        ScheduledLeaveTypeUpdate.Status status;
        try {
            status = ScheduledLeaveTypeUpdate.Status.valueOf(statusFilter.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status '" + statusFilter
                    + "'. Expected one of PENDING, APPLIED, FAILED, CANCELLED.");
        }
        return scheduledLeaveTypeUpdateRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public List<LeaveType> getPendingActivationLeaveTypes() {
        return leaveTypeRepo.findByActiveFalseAndEffectiveStartDateAfter(LocalDate.now());
    }

    // Cancels a brand-new leave type that's sitting inactive, waiting for a future effective
    // date. Only safe when no LeaveBalance rows exist yet for it — if any do, the row's
    // configuration is already in use somewhere and deleting it would orphan that data, so we
    // refuse instead. This permanently discards whatever is currently stored on the row; it does
    // not restore an earlier configuration (relevant if this was a reactivation of a previously
    // deactivated leave type, since addLeaveType overwrites the row's fields immediately even
    // when the reactivation itself is future-dated).
    @Transactional
    @Override
    public ApiResponse<Object> cancelPendingActivation(String leaveTypeId) {
        Optional<LeaveType> leaveTypeOpt = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);
        if (leaveTypeOpt.isEmpty()) {
            return new ApiResponse<>(false, "Leave type " + leaveTypeId + " not found.", null);
        }

        LeaveType leaveType = leaveTypeOpt.get();
        if (Boolean.TRUE.equals(leaveType.getActive())
                || leaveType.getEffectiveStartDate() == null
                || !leaveType.getEffectiveStartDate().isAfter(LocalDate.now())) {
            return new ApiResponse<>(false,
                    "Leave type " + leaveTypeId + " has no pending future activation to cancel.",
                    null);
        }

        List<LeaveBalance> existingBalances = leaveBalanceRepo.findByLeaveType(leaveType);
        if (!existingBalances.isEmpty()) {
            return new ApiResponse<>(false,
                    "Cannot cancel — " + existingBalances.size()
                            + " leave balance record(s) already exist for this leave type.",
                    null);
        }

        leaveTypeRepo.delete(leaveType);
        return new ApiResponse<>(true,
                "Pending activation cancelled; leave type " + leaveTypeId + " removed.",
                null);
    }


    @Override
    public ResponseEntity<LeaveType> getLeaveTypeById(String leaveTypeId) {
        Optional<LeaveType> optionalLeaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);

        return optionalLeaveType.map(leaveType -> new ResponseEntity<>(leaveType, HttpStatus.OK)).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true)
            }
    )
    public ResponseEntity<String> deleteLeaveType(String leaveTypeId) {
        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                .orElseThrow(() -> new LeaveTypeException("Leave type not found."));
        List<LeaveBalance> leaveBalanceList = leaveBalanceRepo.findByLeaveType(leaveType);
        leaveTypeRepo.delete(leaveType);

        // Notify all employees
        notifyAllEmployees(
                "Leave Policy Deleted: " + leaveType.getLeaveName(),
                "leave-policy-deletion-notification.html",
                Map.of("leavePolicyName", leaveType.getLeaveName())
        );

        return new ResponseEntity<>("Leave type deleted successfully", HttpStatus.OK);
    }

    @Transactional
    @Override
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true)
            }
    )
    public ResponseEntity<String> deActiveLeaveType(String leaveTypeId, LocalDate effectiveDate) {

        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                .orElseThrow(() -> new LeaveTypeException("Leave Type Not Found"));

        if (effectiveDate.isAfter(LocalDate.now())) {
            leaveType.setDeactivationEffectiveDate(effectiveDate);
            leaveTypeRepo.save(leaveType);
            return ResponseEntity.ok("Leave type scheduled for deactivation on " + effectiveDate);
        }

        // COMPOFF validation
        if ("L-COMPOFF".equals(leaveType.getLeaveTypeId())) {
            List<LeaveCompoff> compOffList = leaveCompoffRepo
                    .findByStatus(LeaveStatusCompoff.PENDING);
            if (!compOffList.isEmpty()) {
                throw new ApprovalBusinessException(
                        "Cannot deactivate. Pending CompOff requests exist for " + leaveTypeId);
            }
        }

        // General pending requests validation
        List<LeaveRequest> pendingRequests = leaveRequestRepo.findByStatusAndLeaveTypeId(LeaveStatus.PENDING, leaveTypeId);
        if (!pendingRequests.isEmpty()) {
            throw new ApprovalBusinessException(
                    "Cannot deactivate. " + pendingRequests.size() +
                            " pending leave request(s) exist for leave type: " + leaveTypeId);
        }
        // safe to deactivate
        leaveType.setActive(false);
        leaveType.setDeactivationEffectiveDate(LocalDate.now());
        leaveTypeRepo.save(leaveType);
        leaveBalanceRepo.deleteByLeaveType(leaveType);

        return ResponseEntity.ok("Leave type deactivated successfully");
    }


//    @Override
//    public void uploadDocument(String leaveTypeId, MultipartFile file) throws Exception {
//        LeaveType leaveType = leaveTypeRepo.findById(leaveTypeId)
//                .orElseThrow(() -> new LeaveTypeException("Leave type not found"));
//
//        // Only accept PDF or Word files
//        String contentType = file.getContentType();
//        if (!contentType.equalsIgnoreCase("application/pdf") &&
//                !contentType.equalsIgnoreCase("application/msword") &&
//                !contentType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
//            throw new RuntimeException("Only PDF and Word documents are allowed");
//        }
//
//        leaveType.setPolicyDocument(file.getBytes());
//        leaveTypeRepo.save(leaveType);
//    }
//
//    @Override
//    public byte[] viewDocument(String leaveTypeName, String fileType) throws Exception {
//        LeaveType leaveType = leaveTypeRepo.findByLeaveName(leaveTypeName)
//                .orElseThrow(() -> new LeaveTypeException("Leave type not found"));
//
//        if (leaveType.getPolicyDocument() == null) {
//            throw new LeaveTypeException("No document uploaded for this leave type");
//        }
//
//        return leaveType.getPolicyDocument();
//    }
//
//    @Override
//    public void deleteDocument(String leaveTypeId) throws Exception {
//        LeaveType leaveType = leaveTypeRepo.findById(leaveTypeId)
//                .orElseThrow(() -> new LeaveTypeException("Leave type not found"));
//
//        leaveType.setPolicyDocument(null);
//        leaveTypeRepo.save(leaveType);
//    }

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


    public List<LeaveTypeIdDTO> getAllLeaveTypeIds() {
        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();
        List<GenderBasedLeave> genderBasedLeaveTypes = genderBasedRepo.findAll();
        List<LeaveTypeIdDTO> leaveTypeDTOs = new ArrayList<>();

        for (LeaveType leaveType : leaveTypes) {
            if (Boolean.TRUE.equals(leaveType.getActive())) {
                LeaveTypeIdDTO dto = new LeaveTypeIdDTO();
                dto.setLeaveTypeId(leaveType.getLeaveTypeId());
                dto.setLeaveName(leaveType.getLeaveName());
                dto.setActive(leaveType.getActive());
                leaveTypeDTOs.add(dto);
            }
        }

        for (GenderBasedLeave genderBasedLeave : genderBasedLeaveTypes) {
            if (Boolean.TRUE.equals(genderBasedLeave.getActive())) {
                LeaveTypeIdDTO dto = new LeaveTypeIdDTO();
                dto.setLeaveTypeId(genderBasedLeave.getLeaveTypeId());
                dto.setLeaveName(genderBasedLeave.getLeaveName());
                dto.setActive(genderBasedLeave.getActive());
                leaveTypeDTOs.add(dto);
            }
        }

        return leaveTypeDTOs;
    }


    private void notifyAllEmployees(String subject, String template, Map<String, Object> templateModel) {
        List<Employee> employees = employeeRepo.findAll();
        for (Employee employee : employees) {
            if (employee.getEmail() == null || employee.getEmail().isBlank()) {
                log.warn("Employee {} has no email — skipping notification", employee.getEmployeeId());
                continue;
            }
            EmailDTO emailDTO = new EmailDTO(
                    employee.getEmail(),
                    subject,
                    template,
                    true
            );
            emailDTO.setTemplateModel(templateModel);
            asyncNotificationService.queueEmail(emailDTO);
        }
    }

}
