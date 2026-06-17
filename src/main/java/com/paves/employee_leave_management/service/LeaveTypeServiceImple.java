package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.enums.LeaveStatusCompoff;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.globalExceptionHandler.ApprovalBusinessException;
import com.paves.employee_leave_management.repo.*;
import com.paves.employee_leave_management.serviceInterface.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final GenderBasedLeaveServiceInterface genderBasedLeaveServiceInterface;
    private final LeaveBalanceServiceInterface leaveBalanceServiceInterface;
    private final LeaveRequestRepo leaveRequestRepo;
    private final LeaveCompoffRepo leaveCompoffRepo;
    private final EmailServiceInterface emailService;
    private final EmployeeRepo employeeRepo;
    private final LeaveBalanceJobServiceInterface leaveBalanceJobService;
    private final LeaveBalanceJobRepository jobRepository;
    private final AsyncNotificationServiceInterface asyncNotificationService;

    public LeaveTypeServiceImple(
            LeaveTypeRepo leaveTypeRepo,
            LeaveBalanceRepo leaveBalanceRepo,
            GenderBasedRepo genderBasedRepo,
            LeaveBalanceServiceInterface leaveBalanceService,
            GenderBasedLeaveServiceInterface genderBasedLeaveServiceInterface,
            LeaveBalanceServiceInterface leaveBalanceServiceInterface,
            LeaveRequestRepo leaveRequestRepo,
            LeaveCompoffRepo leaveCompoffRepo,
            EmailServiceInterface emailService,
            EmployeeRepo employeeRepo,
            LeaveBalanceJobServiceInterface leaveBalanceJobService,
            LeaveBalanceJobRepository jobRepository,
            AsyncNotificationServiceInterface asyncNotificationService
    ) {
        this.leaveTypeRepo = leaveTypeRepo;
        this.leaveBalanceRepo = leaveBalanceRepo;
        this.genderBasedRepo = genderBasedRepo;
        this.leaveBalanceService = leaveBalanceService;
        this.genderBasedLeaveServiceInterface = genderBasedLeaveServiceInterface;
        this.leaveBalanceServiceInterface = leaveBalanceServiceInterface;
        this.leaveRequestRepo = leaveRequestRepo;
        this.leaveCompoffRepo = leaveCompoffRepo;
        this.emailService = emailService;
        this.employeeRepo = employeeRepo;
        this.leaveBalanceJobService = leaveBalanceJobService;
        this.jobRepository = jobRepository;
        this.asyncNotificationService = asyncNotificationService;
    }



    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
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
                    leaveBalanceService.createLeaveBalanceForAllEmployees(reactivated);
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

    private String startLeaveBalanceJob(LeaveType leaveType, String createdBy) {
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
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ResponseEntity<ApiResponse<Object>> createDirectly(LeaveType leaveType, AdminMaker maker) {
        log.info("Super admin {} creating leave type directly: {}",
                maker.getEmployeeId(), leaveType.getLeaveName());

        // delegate entirely to existing business logic
        ApiResponse<LeaveType> result = addLeaveType(leaveType);

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
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ApiResponse<LeaveType> updateLeaveType(LeaveType updatedLeaveType, String leaveTypeId) {
        Optional<LeaveType> existingOpt = leaveTypeRepo.findByLeaveTypeId(leaveTypeId);
        if (existingOpt.isEmpty()) {
            return new ApiResponse<>(false,
                    "Leave type " + leaveTypeId + " not found.",
                    null);
        }
//        LeaveType existingLeaveType = existingOpt.get();
        double newAccrualRate = 0;
        if (updatedLeaveType.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString()) || updatedLeaveType.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.SICK_LEAVE.toString())) {
            newAccrualRate = (double) updatedLeaveType.getMaxDaysPerYear() / 12;
            newAccrualRate = new BigDecimal(newAccrualRate)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            updatedLeaveType.setAccrualRate(newAccrualRate);
        } else {
            updatedLeaveType.setAccrualRate(newAccrualRate);
        }
        // Save updated LeaveType
        updatedLeaveType.setLastUpdatedAt(LocalDateTime.now());
        updatedLeaveType.setCreateAt(existingOpt.get().getCreateAt());
        updatedLeaveType.setLeaveTypeId(leaveTypeId);
        updatedLeaveType.setActive(existingOpt.get().getActive());
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
             double usedLeaves = balance.getUsedLeaves();
             balance.setRemainingLeaves((balance.getCarriedForward() + recalculatedTotal) - usedLeaves);
        }

        leaveBalanceRepo.saveAll(affectedBalances);

        // Notify all employees
        notifyAllEmployees(
                "Leave Policy Updated: " + savedLeaveType.getLeaveName(),
                "leave-policy-update-notification.html",
                Map.of("leavePolicyName", savedLeaveType.getLeaveName())
        );

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
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ResponseEntity<String> deleteLeaveType(String leaveTypeId) {
        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                .orElseThrow(() -> new RuntimeException("Leave type not found."));
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
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", allEntries = true),
                    @CacheEvict(value = "all-leave-types", allEntries = true),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", allEntries = true),
                    @CacheEvict(value = "pendingLeaveRequestsByEmployeeAndYear", allEntries = true)
            }
    )
    public ResponseEntity<String> deActiveLeaveType(String leaveTypeId, LocalDate effectiveDate) {

        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                .orElseThrow(() -> new RuntimeException("Leave Type Not Found"));

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
