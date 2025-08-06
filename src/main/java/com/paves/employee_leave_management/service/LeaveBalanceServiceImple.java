package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.dto.LeaveBalanceUpdateHandleDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
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
import java.time.Year;
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
            if (leaveBalanceRepo.findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
                    emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear).isPresent()) {
                continue;
            }
            if (emp.getGender() != null) {
                if (emp.getGender().equalsIgnoreCase("male") && lt.getLeaveName().equalsIgnoreCase("Maternity Leave"))
                    continue;
                if (emp.getGender().equalsIgnoreCase("female") && lt.getLeaveName().equalsIgnoreCase("Paternity Leave"))
                    continue;
            }

            double accruedLeaves = 0;
            double totalLeaves = 0;
            double carriedForward = 0;
            double usedLeaves = 0;

            if (lt.getLeaveName().equalsIgnoreCase("Sick Leave")) {
                LocalDate accrualStart = (hireDate.getYear() < currentYear)
                        ? LocalDate.of(currentYear, 1, 1)
                        : hireDate;
                accruedLeaves = getAccruedLeaves(accrualStart, onboardingDate, lt.getAccrualRate());
//                totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                int currYear = Year.now().getValue();

                if (hireDate.getYear() < currYear) {
                    // Hired in previous year, full year accrual
                    totalLeaves = 12 * lt.getAccrualRate();
                } else {
                    // Hired this year, calculate based on remaining months
                    int hireMonth = hireDate.getMonthValue();
                    int monthsLeftInYear = 12 - hireMonth;

                    if (hireDate.getDayOfMonth() < 15) {
                        monthsLeftInYear += 1; // include hire month
                    }

                    totalLeaves = monthsLeftInYear * lt.getAccrualRate();
                }

            } else if (lt.getLeaveName().equalsIgnoreCase("Earned Leave")) {
                LocalDate accrualStart = (hireDate.getYear() < currentYear)
                        ? LocalDate.of(currentYear, 1, 1)
                        : hireDate;
                accruedLeaves = getAccruedLeaves(accrualStart, onboardingDate, lt.getAccrualRate());

                carriedForward = calculateEarnedLeaveCarryForward(hireDate, currentYear,lt);
                int currYear = Year.now().getValue();

                if (hireDate.getYear() < currYear) {
                    // Hired in previous year, full year accrual
                    totalLeaves = 12 * lt.getAccrualRate();
                } else {
                    // Hired this year, calculate based on remaining months
                    int hireMonth = hireDate.getMonthValue();
                    int monthsLeftInYear = 12 - hireMonth;

                    if (hireDate.getDayOfMonth() < 15) {
                        monthsLeftInYear += 1; // include hire month
                    }

                    totalLeaves = monthsLeftInYear * lt.getAccrualRate();
                }
            } else if (lt.getLeaveName().equalsIgnoreCase("Paternity Leave")) {
                accruedLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                totalLeaves = accruedLeaves;
            } else if (lt.getLeaveName().equalsIgnoreCase("Maternity Leave")) {
                accruedLeaves =lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                totalLeaves = accruedLeaves;
            } else {
                totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                accruedLeaves = 0;
            }

            double remainingLeaves = Math.max(0, (accruedLeaves+carriedForward) - usedLeaves);

            LeaveBalance balance = LeaveBalance.builder()
                    .employee(emp)
                    .leaveType(lt)
                    .year(currentYear)
                    .accruedLeaves(accruedLeaves)
                    .carriedForward(carriedForward)
                    .encashedLeaves(0)
                    .expiredLeaves(0.0)
                    .lastAccrualDate(LocalDate.now())
                    .usedLeaves(usedLeaves)
                    .remainingLeaves(remainingLeaves)
                    .totalLeaves(totalLeaves)
                    .build();
            leaveBalanceRepo.save(balance);
        }
    }

    private double getEarnedLeave(LocalDate startDate, LocalDate endDate, double ratePerMonth) {
        LocalDate accrualStart = startDate.getDayOfMonth() > 15
                ? startDate.plusMonths(1).withDayOfMonth(1)
                : startDate.withDayOfMonth(1);
        if (accrualStart.isAfter(endDate)) {
            return 0;
        }
        int months = 0;
        LocalDate iter = accrualStart;
        while (!iter.isAfter(endDate.withDayOfMonth(1))) {
            months++;
            iter = iter.plusMonths(1);
        }
        return months * ratePerMonth;
    }

    private double getAccruedLeaves(LocalDate startDate, LocalDate endDate, double ratePerMonth) {
        if (startDate.isAfter(endDate))
            return 0;
        LocalDate adjustedStart = startDate.getDayOfMonth() > 15 ? startDate.plusMonths(1).withDayOfMonth(1) : startDate.withDayOfMonth(1);
        int months = 0;
        LocalDate iter = adjustedStart;
        while (!iter.isAfter(endDate.withDayOfMonth(1))) {
            months++;
            iter = iter.plusMonths(1);
        }
        return months * ratePerMonth;
    }

    private double calculateEarnedLeaveCarryForward(LocalDate hireDate, int currentYear,LeaveType lt) {
        double totalCarried = 0;
        for (int year = hireDate.getYear(); year < currentYear; year++) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            LocalDate effectiveStart = hireDate.isAfter(yearStart) ? hireDate : yearStart;
            double yearlyAccrued = getEarnedLeave(effectiveStart, yearEnd, lt.getAccrualRate());
            double yearlyCarry = Math.min(yearlyAccrued, lt.getMaxCarryForwardPerYear());  // max carry per year is 10
            totalCarried += yearlyCarry;
            if (totalCarried >= lt.getMaxCarryForward()) {
                return lt.getMaxCarryForward();  // total max cap
            }
        }
        return totalCarried;
    }

    @Override
    public void processYearEndCarryForward() {
        List<LeaveBalance> balances = leaveBalanceRepo.findAllByYear(LocalDate.now().getYear() - 1);
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
                    double forward;
                    if(unused >= carryForward){
                        unused = unused - carryForward;
                        forward = Math.min(balance.getLeaveType().getMaxCarryForwardPerYear(), unused);
                        carryForward = Math.min(balance.getLeaveType().getMaxCarryForward(),carryForward + forward);
                    }else {
                        forward = Math.min(balance.getLeaveType().getMaxCarryForwardPerYear(), unused);
                        carryForward = Math.min(balance.getLeaveType().getMaxCarryForward(), forward);
                    }
                    newbalance.setCarriedForward(carryForward);
                    newbalance.setExpiredLeaves(unused - forward);
                    newbalance.setTotalLeaves(
                            (balance.getLeaveType().getMaxDaysPerYear() != null ? balance.getLeaveType().getMaxDaysPerYear() : 0)
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
        List<LeaveBalance> balances = leaveBalanceRepo.findAllByYear(LocalDate.now().getYear());
        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
        }
        LocalDate now = LocalDate.now();

        if (now.getDayOfMonth() != 1)
            throw new LeaveBalanceExceptionHandler("Accrual can only be triggered on the first day of the month");

        for (LeaveBalance balance : balances) {
            System.out.println(balance.toString());
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
                accrual = type.getAccrualRate() != null ? type.getAccrualRate() : 0;
            }

            if (type.getLeaveName().equalsIgnoreCase("Earned Leave")) {
                accrual = type.getAccrualRate() != null ? type.getAccrualRate() : 0;
                ;
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
        return new ResponseEntity<>(balance, HttpStatus.OK);
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
    public void updateLeaveBalanceAfterApproval(String employeeId, String leaveTypeId, double approvedDays, int year) {
        if (approvedDays <= 0) {
            throw new LeaveBalanceExceptionHandler("Approved days must be greater than 0");
        }
        LeaveBalance balance = leaveBalanceRepo
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);

        balance.setUsedLeaves(balance.getUsedLeaves() + approvedDays);
        if (!leaveTypeId.equalsIgnoreCase("L-UP")) {
            balance.setRemainingLeaves(balance.getRemainingLeaves() - approvedDays);
        }
        leaveBalanceRepo.save(balance);
    }

    @Override
    public void updateLeaveBalanceAfterRejected(String employeeId, String leaveTypeId, double daysRequested, int year) {
        LeaveBalance balance = leaveBalanceRepo
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);

        balance.setUsedLeaves(balance.getUsedLeaves() - daysRequested);
        if (!leaveTypeId.equalsIgnoreCase("L-UP")) {
            balance.setRemainingLeaves(balance.getRemainingLeaves() + daysRequested);
        }
        leaveBalanceRepo.save(balance);
    }

    @Override
    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(List<LeaveBalance> leaveBalance) {
        return new ResponseEntity<>(leaveBalanceRepo.saveAll(leaveBalance), HttpStatus.OK);
    }
    @Override
    public ResponseEntity<String> updateLeaveBalancesFromHr(LeaveBalanceUpdateRequest request) {
        for (LeaveBalanceUpdateRequest.BalanceUpdate update : request.getBalances()) {
            LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                    request.getEmployeeId(),
                    update.getLeaveTypeId(),
                    update.getYear()
            );

            if (balance == null) {
                throw new RuntimeException(
                        "Leave Balance not found for employeeId: " + request.getEmployeeId() +
                                ", leaveTypeId: " + update.getLeaveTypeId() +
                                ", year: " + update.getYear()
                );
            }

            balance.setRemainingLeaves(update.getRemainingLeaves());
            leaveBalanceRepo.save(balance);
        }

        return ResponseEntity.ok("Leave balances updated successfully.");
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
