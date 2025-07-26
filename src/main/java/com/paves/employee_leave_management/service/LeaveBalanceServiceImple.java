package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.globalExceptionHandler.EmployeeExceptionHandler;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveBalanceServiceImple implements LeaveBalanceServiceInterface {

    @Autowired
    LeaveBalanceDAO leaveBalanceDao;

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

    @Autowired
    EmployeeRepo employeeRepo;



    @Override
    public void createLeaveBalanceForNewEmployee(String empId) {
        Employee emp = employeeRepo.findById(empId)
                .orElseThrow(() -> new EmployeeExceptionHandler("Employee not found: " + empId));
        int currentYear = LocalDate.now().getYear();
        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();
        LocalDate onboardingDate = LocalDate.now();
        LocalDate hireDate = emp.getHireDate();

        for (LeaveType lt : leaveTypes) {
            // Skip if a balance already exists for this year and leave type
            if (leaveBalanceRepo.findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
                    emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear).isPresent()) {
                continue;
            }

            // Skip leave types not eligible by gender
            if (emp.getGender() != null) {
                if (emp.getGender().equalsIgnoreCase("male") &&
                        lt.getLeaveName().equalsIgnoreCase("Maternity Leave")) continue;
                if (emp.getGender().equalsIgnoreCase("female") &&
                        lt.getLeaveName().equalsIgnoreCase("Paternity Leave")) continue;
            }

            double accruedLeaves;
            double totalLeaves;
            double carriedForward = 0;
            double usedLeaves = 0; // Assuming no leaves used at onboarding

            if (lt.getLeaveName().equalsIgnoreCase("Sick Leave")) {
                // Sick leave accrues only for current year from Jan 1 or hire date if hired this year, no carry forward
                LocalDate accrualStart = (hireDate.getYear() < currentYear)
                        ? LocalDate.of(currentYear, 1, 1)
                        : hireDate;
                accruedLeaves = getTotalAccruedLeaves(accrualStart, onboardingDate, 1.0);
                totalLeaves = getTotalEntitlement(accrualStart, onboardingDate, 1.0);
                carriedForward = 0;
            } else if (lt.getLeaveName().equalsIgnoreCase("Earned Leave")) {
                // Earned leave accrues only for current year (Jan 1 or hire date), carry forward capped at 10 days
                LocalDate accrualStart = (hireDate.getYear() < currentYear)
                        ? LocalDate.of(currentYear, 1, 1)
                        : hireDate;
                accruedLeaves = getTotalAccruedLeaves(accrualStart, onboardingDate, 1.25);
                totalLeaves = getTotalEntitlement(accrualStart, onboardingDate, 1.25);
                if (onboardingDate.getYear() > hireDate.getYear()) {
                    carriedForward = getCarriedForwardFromLastYear(
                            emp.getEmployeeId(), lt.getLeaveTypeId(), onboardingDate.getYear() - 1
                    );
                }
            } else if (lt.getLeaveName().equalsIgnoreCase("Paternity Leave")) {
                accruedLeaves = 5;
                totalLeaves = 5;
                carriedForward = 0;
            } else if (lt.getLeaveName().equalsIgnoreCase("Maternity Leave")) {
                accruedLeaves = 182;
                totalLeaves = 182;
                carriedForward = 0;
            } else {
                accruedLeaves = 0;
                totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                carriedForward = 0;
            }

            // remainingLeaves = carriedForward + accruedLeaves - usedLeaves (0 for onboarding)
            double remainingLeaves = Math.max(0, carriedForward + accruedLeaves - usedLeaves);

            LeaveBalance balance = LeaveBalance.builder()
                    .employee(emp)
                    .leaveType(lt)
                    .year(currentYear)
                    .totalLeaves(totalLeaves)
                    .accruedLeaves(accruedLeaves)
                    .usedLeaves(usedLeaves)
                    .expiredLeaves(0)
                    .carriedForward(carriedForward)
                    .remainingLeaves(remainingLeaves)
                    .encashedLeaves(0)
                    .lastAccrualDate(onboardingDate)
                    .build();

            leaveBalanceRepo.save(balance);
        }
    }

    /**
     * Calculates accrued leave days between startDate and endDate.
     * Counts full months based on hire date day (after 15th skips hire month).
     */
    private double getTotalAccruedLeaves(LocalDate startDate, LocalDate endDate, double ratePerMonth) {
        if (startDate.isAfter(endDate)) return 0;
        LocalDate start = (startDate.getDayOfMonth() > 15)
                ? startDate.plusMonths(1).withDayOfMonth(1)
                : startDate.withDayOfMonth(1);
        int months = 0;
        LocalDate month = start;
        while (!month.isAfter(endDate.withDayOfMonth(1))) {
            months++;
            month = month.plusMonths(1);
        }
        return months * ratePerMonth;
    }

    /**
     * Retrieves carried forward leave from last year, capped at 10 days.
     */
    private double getCarriedForwardFromLastYear(String empId, String leaveTypeId, int previousYear) {
        Optional<LeaveBalance> prevBalance = leaveBalanceRepo
                .findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
                        empId, leaveTypeId, previousYear);
        if (!prevBalance.isPresent()) return 0;
         double carried_forward =  prevBalance.get().getCarriedForward();
         double remaining_leave = prevBalance.get().getRemainingLeaves();
         double used_leaves = prevBalance.get().getUsedLeaves();
         double total_leaves = prevBalance.get().getTotalLeaves();

         remaining_leave =  carried_forward + total_leaves;
         remaining_leave = remaining_leave - used_leaves;
         remaining_leave = remaining_leave - carried_forward;

         

        return Math.min(10, prevBalance.get().getRemainingLeaves());
    }

    /**
     * Calculates total leave entitlement for the year.
     * For hires before the start of the current year, full year (12 months).
     * For hires this year, pro-rated months including the hire month if day <=15.
     */
    private double getTotalEntitlement(LocalDate hireDate, LocalDate endDate, double ratePerMonth) {
        int year = endDate.getYear();
        if (hireDate.getYear() < year) {
            return 12 * ratePerMonth;
        } else {
            LocalDate startMonth = (hireDate.getDayOfMonth() > 15)
                    ? hireDate.plusMonths(1).withDayOfMonth(1)
                    : hireDate.withDayOfMonth(1);
            LocalDate endMonth = LocalDate.of(year, 12, 1);
            int months = 0;
            while (!startMonth.isAfter(endMonth)) {
                months++;
                startMonth = startMonth.plusMonths(1);
            }
            return months * ratePerMonth;
        }
    }





    @Override
    public void processYearEndCarryForward() {
        List<LeaveBalance> balances = leaveBalanceRepo.findAll();
        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
        }

        for (LeaveBalance balance : balances) {
            LeaveBalance newbalance = new LeaveBalance();
            newbalance.setEmployee(balance.getEmployee());
            newbalance.setLeaveType(balance.getLeaveType());

            String name = balance.getLeaveType().getLeaveName();
            double unused = balance.getRemainingLeaves();
            double carryForward = balance.getCarriedForward();

            switch (name) {
                case "Earned Leave":
                    double forward = Math.min(10, unused);
                    carryForward = Math.min(48, carryForward + forward);
                    newbalance.setCarriedForward(carryForward);
                    newbalance.setExpiredLeaves(unused - forward);
                    newbalance.setTotalLeaves(
                            (balance.getLeaveType().getMaxDaysPerYear() != null ? balance.getLeaveType().getMaxDaysPerYear() : 0) + carryForward
                    );
                    newbalance.setAccruedLeaves(balance.getAccruedLeaves());
                    break;
                case "Sick Leave":
                    newbalance.setCarriedForward(0);
                    newbalance.setExpiredLeaves(unused);
                    newbalance.setTotalLeaves(balance.getLeaveType().getMaxDaysPerYear() != null ? balance.getLeaveType().getMaxDaysPerYear() : 0);
                    newbalance.setAccruedLeaves(0);
                    break;
                default:
                    newbalance.setCarriedForward(0);
                    newbalance.setExpiredLeaves(unused);
            }

            newbalance.setYear(balance.getYear() + 1);
            newbalance.setLastAccrualDate(LocalDate.now());
            newbalance.setUsedLeaves(0);
            newbalance.setEncashedLeaves(0);
            newbalance.updateRemainingLeaves();
            leaveBalanceDao.save(newbalance);
        }
    }

    @Scheduled(cron = "0 0 0 1 1 *")
    public void scheduleYearEndProcessing() {
        processYearEndCarryForward();
    }

    @Scheduled(cron = "0 5 0 1 * *")
    public void scheduleMonthlyLeaveAccrual() {
        triggerMonthlyLeaveAccrual();
    }

    @Override
    public void triggerMonthlyLeaveAccrual() {
        List<LeaveBalance> balances = leaveBalanceRepo.findAll();
        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
        }
        LocalDate now = LocalDate.now();

        if (now.getDayOfMonth() != 1) return;

        for (LeaveBalance balance : balances) {
            Employee emp = balance.getEmployee();
            LeaveType type = balance.getLeaveType();
            LocalDate hireDate = emp.getHireDate();
            LocalDate accrualDate = balance.getLastAccrualDate();

            if (hireDate.isAfter(now.withDayOfMonth(1))) continue;

            if (accrualDate != null &&
                    accrualDate.getMonth() == now.getMonth() &&
                    accrualDate.getYear() == now.getYear()) {
                continue;
            }

            double accrual = 0;

            if (type.getLeaveName().equalsIgnoreCase("Sick Leave")) {
                accrual = 1.0;
            }

            if (type.getLeaveName().equalsIgnoreCase("Earned Leave")) {
                accrual = 1.25;
            }

            if (accrual > 0) {
                balance.setAccruedLeaves(balance.getAccruedLeaves() + accrual);
                balance.updateRemainingLeaves();
                balance.setLastAccrualDate(now);
                leaveBalanceDao.save(balance);
            }
        }
    }

    @Override
    public ResponseEntity<LeaveBalance> findByBalanceId(String balanceId) {
        LeaveBalance balance = leaveBalanceDao.findById(balanceId);
        if (balance == null) {
            throw new LeaveBalanceExceptionHandler("Balance not found: " + balanceId);
        }
        return new ResponseEntity<>(balance, HttpStatus.FOUND);
    }

    @Override
    public ResponseEntity<List<LeaveBalance>> getAllLeaveBalances() {
        List<LeaveBalance> balance = leaveBalanceDao.findAll();
        if (balance.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No records Found");
        }
        return new ResponseEntity<>(balance, HttpStatus.FOUND);
    }

    @Override
    public ResponseEntity<List<LeaveBalance>> findByEmployeeId(String employeeId) {
        List<LeaveBalance> balance = leaveBalanceDao.findByEmployeeId(employeeId);
        if (balance.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("Leave Balances not found for employee: " + employeeId);
        }
        return new ResponseEntity<>(balance, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<LeaveBalance>> findByLeaveId(String leaveId) {
        List<LeaveBalance> balance = leaveBalanceDao.findByLeaveId(leaveId);
        if (balance.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("Leave Balances not found for leave name : " + leaveId);
        }
        return new ResponseEntity<>(balance, HttpStatus.FOUND);
    }

    @Transactional
    @Override
    public void updateLeaveBalanceAfterApproval(String employeeId, String leaveTypeId, double approvedDays) {
        if (approvedDays <= 0) {
            throw new LeaveBalanceExceptionHandler("Approved days must be greater than 0");
        }

        LeaveBalance balance = leaveBalanceRepo
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeId(employeeId, leaveTypeId)
                .orElseThrow(() -> new LeaveBalanceExceptionHandler("Leave balance not found for employee " + employeeId + " and leave type " + leaveTypeId));

        balance.setUsedLeaves(balance.getUsedLeaves() + approvedDays);
        balance.updateRemainingLeaves();

        leaveBalanceRepo.save(balance);
    }

    @Override
    public LeaveBalanceDTO getLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
        if (balance == null) {
            return null;
        }
        return LeaveBalanceDTO.builder()
                .balanceId(balance.getBalanceId())
                .employeeId(balance.getEmployee().getEmployeeId())
                .employeeName(balance.getEmployee().getFullName())
                .leaveTypeId(balance.getLeaveType().getLeaveTypeId())
                .leaveTypeName(balance.getLeaveType().getLeaveName())
                .totalLeaves(balance.getTotalLeaves())
                .accruedLeaves(balance.getAccruedLeaves())
                .usedLeaves(balance.getUsedLeaves())
                .remainingLeaves(balance.getRemainingLeaves())
                .carriedForward(balance.getCarriedForward())
                .year(balance.getYear())
                .build();
    }
}
