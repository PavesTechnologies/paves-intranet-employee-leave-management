package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.EmployeeStatus;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveTypeException;
import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceJobRepository;
import com.paves.employee_leave_management.repo.ScheduledLeaveTypeUpdateRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.AsyncNotificationServiceInterface;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceJobServiceInterface;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
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
    private ScheduledLeaveTypeUpdateRepo scheduledLeaveTypeUpdateRepo;

    @Autowired
    private LeaveBalanceJobRepository leaveBalanceJobRepository;

    @Autowired
    private LeaveBalanceJobServiceInterface leaveBalanceJobService;

    @Autowired
    private AsyncNotificationServiceInterface asyncNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

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
    public ApiResponse<Object> createGenderBaseLeave(GenderBasedLeave genderBaseLeave) {
        genderBaseLeave.generateId();

        Optional<GenderBasedLeave> existing = genderBasedRepo.findById(genderBaseLeave.getLeaveTypeId());

        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActive())) {
            return new ApiResponse<>(false,
                    "Leave type already exists and is active",
                    null);
        }

        // BUG FIX: active previously was hardcoded true even when future-dated, so a scheduled
        // gender-based leave type looked live immediately (new hires got a balance for it before
        // its effective date) while existing employees never got backfilled — nothing ever
        // revisited it since it never looked "pending". Mirrors the regular LeaveType path.
        boolean shouldActivateNow = !genderBaseLeave.getEffectiveStartDate().isAfter(LocalDate.now());
        genderBaseLeave.setActive(shouldActivateNow);
        genderBaseLeave.setCreatedAt(LocalDateTime.now());
        genderBaseLeave.setEffectiveEndDate(null);
        GenderBasedLeave saved = genderBasedRepo.save(genderBaseLeave);

        if (shouldActivateNow) {
            startGenderBasedLeaveBalanceJob(saved, "SYSTEM");
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
    public String startGenderBasedLeaveBalanceJob(GenderBasedLeave leaveType, String createdBy) {
        LeaveBalanceJob job = LeaveBalanceJob.builder()
                .leaveTypeId(leaveType.getLeaveTypeId())
                .leaveTypeName(leaveType.getLeaveName())
                .status(LeaveBalanceJob.JobStatus.PENDING)
                .leaveCategory(LeaveBalanceJob.LeaveCategory.GENDER_BASED)
                .createdBy(createdBy)
                .build();

        LeaveBalanceJob saved = leaveBalanceJobRepository.save(job);
        leaveBalanceJobService.processLeaveBalancesAsync(saved.getJobId(), leaveType.getLeaveTypeId());
        return saved.getJobId();
    }

    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "leaveBalanceByEmployeeAndLeaveType", allEntries = true),
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true)

            }
    )
    public ResponseEntity<ApiResponse<Object>> createGenderBasedDirectly(
            GenderBasedLeave genderBaseLeave, Employee maker) {

        log.info("Super admin {} creating gender based leave directly: {}",
                maker.getEmployeeId(), genderBaseLeave.getLeaveName());

        // check if already exists and active — same as validateGenderBaseLeave
        Optional<GenderBasedLeave> existing = genderBasedRepo
                .findByLeaveNameIgnoreCase(genderBaseLeave.getLeaveName());

        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActive())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(false,
                            "Leave type already exists and is active", null));
        }

        // delegate to existing business logic
        ApiResponse<Object> result = createGenderBaseLeave(genderBaseLeave);

        if (!result.isSuccess()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(false, result.getMessage(), null));
        }

        log.info("Gender based leave created directly by super admin: {} result: {}",
                maker.getEmployeeId(), result.getMessage());

        return ResponseEntity.ok(new ApiResponse<>(true, result.getMessage(), result.getData()));
    }

    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "leaveBalanceByEmployeeAndLeaveType", allEntries = true),
                    @CacheEvict(value = "employeeLeaveBalanceForDropdown", allEntries = true)
            }
    )
    public ApiResponse<Object> updateGenderBaseLeave(GenderBasedLeave genderBaseLeave, String leaveTypeId) {
        Optional<GenderBasedLeave> existing = genderBasedRepo.findById(leaveTypeId);
        if(existing.isEmpty()){
            return new ApiResponse<>(false,
                    "Leave type not found",
                    null);
        }

        GenderBasedLeave existingLeave = existing.get();
        LocalDate effectiveDate = genderBaseLeave.getEffectiveStartDate();
        boolean isFutureDated = effectiveDate != null && effectiveDate.isAfter(LocalDate.now());

        if (isFutureDated) {
            Optional<ScheduledLeaveTypeUpdate> existingPending = scheduledLeaveTypeUpdateRepo
                    .findByLeaveTypeIdAndLeaveCategoryAndStatus(
                            leaveTypeId,
                            ScheduledLeaveTypeUpdate.LeaveCategory.GENDER_BASED,
                            ScheduledLeaveTypeUpdate.Status.PENDING);

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
                payload = objectMapper.writeValueAsString(genderBaseLeave);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing scheduled gender-based leave update", e);
            }

            ScheduledLeaveTypeUpdate scheduled = ScheduledLeaveTypeUpdate.builder()
                    .leaveTypeId(leaveTypeId)
                    .effectiveDate(effectiveDate)
                    .payload(payload)
                    .status(ScheduledLeaveTypeUpdate.Status.PENDING)
                    .leaveCategory(ScheduledLeaveTypeUpdate.LeaveCategory.GENDER_BASED)
                    .build();
            scheduledLeaveTypeUpdateRepo.save(scheduled);

            log.info("Scheduled gender-based leave update {} for leave type {} effective {}",
                    scheduled.getId(), leaveTypeId, effectiveDate);

            return new ApiResponse<>(true,
                    "Update scheduled to take effect on " + effectiveDate + ".",
                    null);
        }

        GenderBasedLeave saved = applyGenderBasedLeaveUpdate(genderBaseLeave, existingLeave);

        return new ApiResponse<>(true,
                "Leave type updated successfully. Eligible employees' balances have been recalculated.",
                saved);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void applyScheduledGenderBasedUpdate(ScheduledLeaveTypeUpdate scheduled) {
        try {
            GenderBasedLeave incoming = objectMapper.readValue(scheduled.getPayload(), GenderBasedLeave.class);
            GenderBasedLeave existing = genderBasedRepo.findByLeaveTypeId(scheduled.getLeaveTypeId())
                    .orElseThrow(() -> new LeaveTypeException(
                            "Leave type not found: " + scheduled.getLeaveTypeId()));

            applyGenderBasedLeaveUpdate(incoming, existing);

            scheduled.setStatus(ScheduledLeaveTypeUpdate.Status.APPLIED);
            scheduled.setAppliedAt(LocalDateTime.now());
            scheduledLeaveTypeUpdateRepo.save(scheduled);

            log.info("Applied scheduled gender-based leave update {} for leave type {}",
                    scheduled.getId(), scheduled.getLeaveTypeId());
        } catch (Exception e) {
            log.error("Failed to apply scheduled gender-based leave update {} for leave type {}: {}",
                    scheduled.getId(), scheduled.getLeaveTypeId(), e.getMessage(), e);
            scheduled.setStatus(ScheduledLeaveTypeUpdate.Status.FAILED);
            scheduled.setErrorMessage(e.getMessage());
            scheduled.setAppliedAt(LocalDateTime.now());
            scheduledLeaveTypeUpdateRepo.save(scheduled);
        }
    }

    // Persists the incoming policy fields. If ANY field changed, notifies every ACTIVE employee
    // who currently holds a current-year balance row for this leave type — minLeaveDays and
    // MaxNoOfTimes are read live off this row at request-validation time (LeaveRequestService),
    // so they need no balance migration, but employees still deserve to know the policy moved.
    // Only when maxLeaveDays specifically changed do we also recalculate totalEntitledDays /
    // remainingDays on those same balance rows. Shared by the immediate-update path above and
    // applyScheduledGenderBasedUpdate() below, so there's exactly one place where "an update
    // happens" for gender-based leave, mirroring LeaveTypeServiceImple.applyLeaveTypeUpdate.
    private GenderBasedLeave applyGenderBasedLeaveUpdate(GenderBasedLeave incoming, GenderBasedLeave existing) {
        boolean policyChanged = hasPolicyChanged(incoming, existing);
        int previousMaxLeaveDays = existing.getMaxLeaveDays() == null ? 0 : existing.getMaxLeaveDays();

        incoming.setLeaveTypeId(existing.getLeaveTypeId());
        incoming.setCreatedAt(existing.getCreatedAt());
        incoming.setUpdatedAt(LocalDateTime.now());
        GenderBasedLeave saved = genderBasedRepo.save(incoming);

        if (!policyChanged) {
            return saved;
        }

        int newMaxLeaveDays = saved.getMaxLeaveDays() == null ? 0 : saved.getMaxLeaveDays();
        boolean maxLeaveDaysChanged = newMaxLeaveDays != previousMaxLeaveDays;

        // Scoped to the current year and non-deleted rows only, same reasoning as the regular
        // leave-type recalculation: touching prior years would corrupt closed-year history.
        int currentYear = LocalDate.now().getYear();
        List<GenderBasedLeaveBalance> balances = genderBasedLeaveBalancesRepo
                .findByLeaveTypeIdAndYearNotDeleted(saved.getLeaveTypeId(), currentYear);

        if (balances.isEmpty()) {
            return saved;
        }

        // Only ACTIVE employees are touched/notified — resigned/terminated/inactive employees'
        // rows are left as historical record.
        List<String> employeeIds = balances.stream()
                .map(GenderBasedLeaveBalance::getEmployeeId)
                .toList();
        Map<String, Employee> activeEmployeesById = employeeRepo
                .findByEmployeeIdInAndStatus(employeeIds, EmployeeStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));

        List<GenderBasedLeaveBalance> activeBalances = balances.stream()
                .filter(balance -> activeEmployeesById.containsKey(balance.getEmployeeId()))
                .toList();

        if (activeBalances.isEmpty()) {
            return saved;
        }

        if (maxLeaveDaysChanged) {
            // No clamping on remainingDays — if maxLeaveDays drops below usedDays, remainingDays
            // simply goes negative, exactly like the regular-leave recalculation; enforcing
            // allowNegativeBalance is a request-approval-time concern, not this recalculation's job.
            for (GenderBasedLeaveBalance balance : activeBalances) {
                int usedDays = balance.getUsedDays() == null ? 0 : balance.getUsedDays();
                balance.setTotalEntitledDays(newMaxLeaveDays);
                balance.setRemainingDays(newMaxLeaveDays - usedDays);
                balance.setUpdatedAt(LocalDateTime.now());
            }
            genderBasedLeaveBalancesRepo.saveAll(activeBalances);
        }

        notifyAffectedEmployees(saved, activeBalances, activeEmployeesById);

        return saved;
    }

    private boolean hasPolicyChanged(GenderBasedLeave incoming, GenderBasedLeave existing) {
        return !Objects.equals(incoming.getLeaveName(), existing.getLeaveName())
                || !Objects.equals(incoming.getMaxLeaveDays(), existing.getMaxLeaveDays())
                || !Objects.equals(incoming.getMinLeaveDays(), existing.getMinLeaveDays())
                || !Objects.equals(incoming.getWaitingPeriodDays(), existing.getWaitingPeriodDays())
                || !Objects.equals(incoming.getRequiresDocumentation(), existing.getRequiresDocumentation())
                || !Objects.equals(incoming.getAllowNegativeBalance(), existing.getAllowNegativeBalance())
                || !Objects.equals(incoming.getGender(), existing.getGender())
                || !Objects.equals(incoming.getAdvanceNotice(), existing.getAdvanceNotice())
                || !Objects.equals(incoming.getCoolDownPeriod(), existing.getCoolDownPeriod())
                || !Objects.equals(incoming.getNoticePeriodRestrictions(), existing.getNoticePeriodRestrictions())
                || !Objects.equals(incoming.getActive(), existing.getActive())
                || !Objects.equals(incoming.getEffectiveStartDate(), existing.getEffectiveStartDate())
                || !Objects.equals(incoming.getEffectiveEndDate(), existing.getEffectiveEndDate())
                || !Objects.equals(incoming.getWeekendsAndHolidaysAllowed(), existing.getWeekendsAndHolidaysAllowed())
                || !Objects.equals(incoming.getMaxNoOfTimes(), existing.getMaxNoOfTimes())
                || !Objects.equals(incoming.getDescription(), existing.getDescription());
    }

    private void notifyAffectedEmployees(GenderBasedLeave leaveType, List<GenderBasedLeaveBalance> affected,
                                          Map<String, Employee> activeEmployeesById) {
        for (GenderBasedLeaveBalance balance : affected) {
            Employee employee = activeEmployeesById.get(balance.getEmployeeId());
            if (employee == null || employee.getEmail() == null || employee.getEmail().isBlank()) {
                continue;
            }

            EmailDTO emailDTO = new EmailDTO(
                    employee.getEmail(),
                    "Leave Policy Updated: " + leaveType.getLeaveName(),
                    "leave-policy-update-notification.html",
                    true
            );
            emailDTO.setTemplateModel(Map.of("leavePolicyName", leaveType.getLeaveName()));
            asyncNotificationService.queueEmail(emailDTO);
        }
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
            genderBasedRepo.save(existing);
            return new ApiResponse<>(true,
                    "Leave type scheduled for deactivation on " + effectiveDate,
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
