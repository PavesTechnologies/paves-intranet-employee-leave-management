package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.ScheduledLeaveTypeUpdate;
import com.paves.employee_leave_management.enums.BlockStatus;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveBlockRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.repo.ScheduledLeaveTypeUpdateRepo;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveTypeServiceInterface;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBlockScheduler {

    private final LeaveBlockRepo leaveBlockRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final LeaveTypeRepo leaveTypeRepo;
    private final LeaveBalanceServiceInterface leaveBalanceServiceInterface;
    private final LeaveRequestRepo leaveRequestRepo;
    private final EmailServiceInterface emailService;
    private final EmployeeRepo employeeRepo;
    private final ScheduledLeaveTypeUpdateRepo scheduledLeaveTypeUpdateRepo;
    private final LeaveTypeServiceInterface leaveTypeService;
    private final GenderBasedRepo genderBasedRepo;
    private final GenderBasedLeaveBalancesRepo genderBasedLeaveBalancesRepo;
    private final GenderBasedLeaveServiceInterface genderBaseLeaveService;

    @Transactional
    public void processLeaveBlock() {
        LocalDate today = LocalDate.now();

        // 1️⃣ Activate pending blocks whose start date has arrived
        List<LeaveBlock> toActivate = leaveBlockRepo.findByStatusAndStartDateLessThanEqual(BlockStatus.PENDING, today);
        for (LeaveBlock leaveBlock : toActivate) {
            leaveBlock.setStatus(BlockStatus.ACTIVE);
            leaveBlockRepo.save(leaveBlock);

            List<LeaveBalance> balancesToUpdate = new ArrayList<>();
            leaveBlock.getMembers().forEach(member -> {
                leaveBlock.getLeaveTypes().forEach(type -> {
                    LeaveBalance balance = leaveBalanceRepo
                            .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                                    member.getEmployeeId(),
                                    type.getLeaveTypeId(),
                                    today.getYear()
                            );
                    if (balance != null) {
                        balance.setBlockId(leaveBlock.getId());
                        balance.setIsBlocked(true);
                        balancesToUpdate.add(balance);
                    }
                });
            });
            if (!balancesToUpdate.isEmpty()) leaveBalanceRepo.saveAll(balancesToUpdate);
        }

        // 2️⃣ Expire active blocks whose end date has passed
        List<LeaveBlock> toExpire = leaveBlockRepo.findByStatusAndEndDateBefore(BlockStatus.ACTIVE, today);
        for (LeaveBlock leaveBlock : toExpire) {
            leaveBlock.setStatus(BlockStatus.INACTIVE);
            leaveBlockRepo.save(leaveBlock);

            List<LeaveBalance> balances = leaveBalanceRepo.findByBlockId(leaveBlock.getId());
            balances.forEach(balance -> {
                balance.setBlockId(null);
                balance.setIsBlocked(false);
            });
            if (!balances.isEmpty()) leaveBalanceRepo.saveAll(balances);
        }
    }

    public void activatePendingLeaveTypes() {
        List<LeaveType> pendingTypes = leaveTypeRepo.findPendingEffectiveLeaveTypes();

        if (pendingTypes.isEmpty()) return;

        log.info("Activating {} leave types effective today...", pendingTypes.size());

        for (LeaveType leaveType : pendingTypes) {
            leaveType.setActive(true);
            leaveTypeRepo.save(leaveType);
            leaveBalanceServiceInterface.createLeaveBalanceForAllEmployees(leaveType);
            log.info("Activated leave type: {} (effective from {})",
                    leaveType.getLeaveName(),
                    leaveType.getEffectiveStartDate());
        }
    }

    public void applyScheduledLeaveTypeUpdates() {
        LocalDate today = LocalDate.now();
        List<ScheduledLeaveTypeUpdate> due = scheduledLeaveTypeUpdateRepo
                .findByStatusAndEffectiveDateLessThanEqual(ScheduledLeaveTypeUpdate.Status.PENDING, today);

        if (due.isEmpty()) return;

        log.info("Applying {} scheduled leave type update(s)...", due.size());

        for (ScheduledLeaveTypeUpdate scheduled : due) {
            if (scheduled.getLeaveCategory() == ScheduledLeaveTypeUpdate.LeaveCategory.GENDER_BASED) {
                genderBaseLeaveService.applyScheduledGenderBasedUpdate(scheduled);
            } else {
                leaveTypeService.applyScheduledUpdate(scheduled);
            }
        }
    }

    @Transactional
    public void deactivateDueLeaveTypes() {
        LocalDate today = LocalDate.now();

        // Fetch active leave types with deactivation date <= today
        List<LeaveType> toDeactivate = leaveTypeRepo.findByActiveTrueAndDeactivationEffectiveDateLessThanEqual(today);
        if (toDeactivate.isEmpty()) return;

        log.info("Deactivating {} leave types effective today or earlier...", toDeactivate.size());
        for (LeaveType leaveType : toDeactivate) {
            leaveType.setActive(false);
            leaveType.setDeactivationEffectiveDate(null);
            System.out.println(leaveType);
            leaveTypeRepo.save(leaveType);
            leaveRequestRepo.deleteByLeaveTypeAndStatus(leaveType, LeaveStatus.PENDING);
            // Optional cleanup of leave balances linked to the deactivated leave type
            leaveBalanceRepo.deleteByLeaveType(leaveType);

            log.info("Deactivated leave type: {} (effective until {})",
                    leaveType.getLeaveName(),
                    leaveType.getDeactivationEffectiveDate());
        }
    }

    @Transactional
    public void deactivateDueGenderBasedLeaveTypes() {
        LocalDate today = LocalDate.now();

        List<GenderBasedLeave> toDeactivate =
                genderBasedRepo.findByActiveTrueAndEffectiveEndDateLessThanEqual(today);
        if (toDeactivate.isEmpty()) return;

        log.info("Deactivating {} gender-based leave type(s) effective today or earlier...", toDeactivate.size());

        for (GenderBasedLeave leaveType : toDeactivate) {
            leaveType.setActive(false);
            leaveType.setEffectiveEndDate(null);
            genderBasedRepo.save(leaveType);
            genderBasedLeaveBalancesRepo.deleteByLeaveType_LeaveTypeId(leaveType.getLeaveTypeId());

            log.info("Deactivated gender-based leave type: {}", leaveType.getLeaveName());
        }
    }

    public void sendDailyLeaveDigest() {
        List<LeaveRequest> onLeaveToday = leaveRequestRepo.findTodayApproved();

        Map<String, String> employeesOnLeave = onLeaveToday.stream()
                .filter(lr -> !lr.getLeaveType().getLeaveTypeId().equalsIgnoreCase("L-SICK"))
                .collect(Collectors.toMap(
                        lr -> lr.getEmployee().getFirstName() + " " + lr.getEmployee().getLastName(),
                        lr -> lr.getLeaveType().getLeaveName(),
                        (v1, v2) -> v1, // In case of duplicates, keep the first one
                        LinkedHashMap::new
                ));

        if (!employeesOnLeave.isEmpty()) {
            List<Employee> allEmployees = employeeRepo.findAll();
            String[] recipientEmails = allEmployees.stream()
                                                   .map(Employee::getEmail)
                                                   .toArray(String[]::new);

            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", "Daily Leave Digest");
            templateModel.put("recipientName", "Team"); // Generic recipient name
            templateModel.put("messageBody", "Here is the list of employees on leave today:");
            templateModel.put("detailsTitle", "Employees on Leave");
            templateModel.put("details", employeesOnLeave);

            emailService.sendBulkEmailFromTemplate(recipientEmails, "Daily Leave Digest", "generic-notification.html", templateModel);
        }
    }
}