package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.entities.*;
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

        for (LeaveType lt : leaveTypes) {
            if (leaveBalanceDao.existsByEmployeeIdAndLeaveTypeIdAndYear(emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear))
                continue;

            if (emp.getGender().equalsIgnoreCase("male") && lt.getLeaveName().equalsIgnoreCase("Maternity Leave"))
                continue;

            if (emp.getGender().equalsIgnoreCase("female") && lt.getLeaveName().equalsIgnoreCase("Paternity Leave"))
                continue;

            int monthsEligible = getEligibleMonths(emp.getHireDate(), currentYear);
            double totalLeaves = calculateTotalLeaves(lt, monthsEligible);
            double accruedLeaves = 0;
            if (emp.getHireDate().getDayOfMonth() <= 15) {
                if (lt.getLeaveName().equalsIgnoreCase("Sick Leave"))
                    accruedLeaves = 1;

                if (lt.getLeaveName().equalsIgnoreCase("Earned Leave"))
                    accruedLeaves = 1.25;
            }
            if (lt.getLeaveName().equalsIgnoreCase("Paternity Leave"))
            {
                accruedLeaves = 5;
                totalLeaves = 10;
            }
            if (lt.getLeaveName().equalsIgnoreCase("Maternity Leave"))
            {
                totalLeaves = 364;
            }

            LeaveBalance balance = LeaveBalance.builder()
                    .employee(emp)
                    .leaveType(lt)
                    .year(currentYear)
                    .totalLeaves(totalLeaves)
                    .accruedLeaves(accruedLeaves)
                    .usedLeaves(0)
                    .expiredLeaves(0)
                    .carriedForward(0)
                    .remainingLeaves(totalLeaves)
                    .encashedLeaves(0)
                    .lastAccrualDate(null)
                    .build();

            leaveBalanceDao.save(balance);
        }
    }

    private int getEligibleMonths(LocalDate hireDate, int year) {
        if (hireDate.getYear() > year) return 0;

        LocalDate start = (hireDate.getYear() == year) ? hireDate : LocalDate.of(year, 1, 1);
        int months = 0;
        while (!start.isAfter(LocalDate.of(year, 12, 1))) {
            if (start.getDayOfMonth() <= 15) {
                months++;
            }
            start = start.plusMonths(1).withDayOfMonth(1);
        }
        return months;
    }

    private double calculateTotalLeaves(LeaveType leaveType, int monthsEligible) {
        String leaveName = leaveType.getLeaveName().toLowerCase();

        if (leaveName.equalsIgnoreCase("Sick Leave")) {
            return monthsEligible * 1.0;
        } else if (leaveName.equalsIgnoreCase("Earned Leave")) {
            return monthsEligible * 1.25;
        }
        else{
            return leaveType.getMaxDaysPerYear() != null ? leaveType.getMaxDaysPerYear() : 0;
        }
    }

    @Override
    public void processYearEndCarryForward() {
        List<LeaveBalance> balances = leaveBalanceRepo.findAll();
        if(balances.isEmpty())
        {
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
                    carryForward = Math.min(48,carryForward + forward);
                    newbalance.setCarriedForward(carryForward);
                    newbalance.setExpiredLeaves(unused - forward);
                    newbalance.setTotalLeaves(balance.getLeaveType().getMaxDaysPerYear() != null ? balance.getLeaveType().getMaxDaysPerYear() : 0 + carryForward);
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
        if(balances.isEmpty())
        {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
        }
        LocalDate now = LocalDate.now();
        if(now.getDayOfMonth() != 1) return;
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
        return new ResponseEntity<>(balance, HttpStatus.FOUND);
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

}
