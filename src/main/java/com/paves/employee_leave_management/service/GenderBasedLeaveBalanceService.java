package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GenderBasedLeaveBalanceService implements GenderBasedLeaveBalanceServiceInterface {
    @Autowired
    private GenderBasedLeaveBalancesRepo leaveBalanceRepo;

    @Autowired
    private GenderBasedRepo genderBasedRepo;

    @Autowired
    private EmployeeRepo employeeRepo;


    public GenderBasedLeaveBalance buildLeaveBalance(Employee employee, GenderBasedLeave leaveType, LocalDateTime createdDate, boolean isActive) {
        GenderBasedLeaveBalance leaveBalance = new GenderBasedLeaveBalance();
        leaveBalance.setEmployeeId(employee.getEmployeeId());
        leaveBalance.setLeaveType(genderBasedRepo.findByLeaveTypeId(leaveType.getLeaveTypeId()).get());
        leaveBalance.setTotalEntitledDays(leaveType.getMaxLeaveDays());
        leaveBalance.setUsedDays(0);
        leaveBalance.setRemainingDays(leaveType.getMaxLeaveDays());
        leaveBalance.setYear(LocalDate.now().getYear());
        leaveBalance.setCreatedAt(createdDate);
        leaveBalance.setUpdatedAt(createdDate);
        return leaveBalance;
    }

    @Override
    public void createLeaveBalanceForAllEmployees(GenderBasedLeave leaveType) {
        int year = LocalDate.now().getYear();
        LocalDateTime createdDate = LocalDateTime.now();

        List<Employee> employees = employeeRepo.findAll();

        List<GenderBasedLeaveBalance> newBalances = employees.stream()
                .filter(emp -> {
                    // Skip maternity for males
                    if (leaveType.getLeaveName().equalsIgnoreCase("MATERNITY_LEAVE")
                            && emp.getGender().equalsIgnoreCase("MALE")) {
                        return false;
                    }

                    // Skip paternity for females
                    if (leaveType.getLeaveName().equalsIgnoreCase("PATERNITY_LEAVE")
                            && emp.getGender().equalsIgnoreCase("FEMALE")) {
                        return false;
                    }

                    // Include only if no existing record for this employee + leave type + year
                    return leaveBalanceRepo.findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                            emp.getEmployeeId(),
                            leaveType.getLeaveTypeId(),
                            year
                    ).isEmpty();
                })
                .map(emp -> buildLeaveBalance(emp, leaveType, createdDate, true))
                .toList();


        if (!newBalances.isEmpty()) {
            leaveBalanceRepo.saveAll(newBalances);
        }
    }

    @Override
    public void updateLeaveBalanceForEmployee(GenderBasedLeave genderBasedLeave, String employeeId) {

    }

    @Override
    public List<GenderBasedLeaveBalance> getCurrentYearBalances(String employeeId) {
        return leaveBalanceRepo.findByEmployeeIdAndYear(employeeId, LocalDate.now().getYear());
    }

    @Override
    public GenderBasedLeaveBalance getCurrentYearBalancesForEmployee(String employeeId) {
        int year = LocalDate.now().getYear();
        return leaveBalanceRepo.findByYearAndEmployeeId(year, employeeId);
    }

    @Transactional
    @Override
    public void updateLeaveBalanceAfterApproval(String employeeId, String leaveTypeId, double approvedDays, int year) {
        if (approvedDays <= 0) {
            throw new LeaveBalanceExceptionHandler("Approved days must be greater than 0");
        }
        GenderBasedLeaveBalance balance = leaveBalanceRepo
                .findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year).get();

        balance.setUsedDays((int)(balance.getUsedDays() + approvedDays));
        if (!leaveTypeId.equalsIgnoreCase("L-UP")) {
            balance.setRemainingDays((int)(balance.getRemainingDays() - approvedDays));
        }
        leaveBalanceRepo.save(balance);
    }

    @Transactional
    @Override
    public void updateLeaveBalanceAfterRejected(String employeeId, String leaveTypeId, double rejectedDays, int year) {
        if (rejectedDays <= 0) {
            throw new LeaveBalanceExceptionHandler("Rejected days must be greater than 0");
        }

        GenderBasedLeaveBalance balance = leaveBalanceRepo
                .findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElseThrow(() -> new LeaveBalanceExceptionHandler(
                        "Gender based leave balance not found for employee: " + employeeId));

        // ✅ Reverse the deduction — add days back
        int restoredUsed = (int) Math.max(0, balance.getUsedDays() - rejectedDays);
        balance.setUsedDays(restoredUsed);

        // ✅ Only restore remaining if not unpaid leave
        if (!leaveTypeId.equalsIgnoreCase("L-UP")) {
            int restoredRemaining = (int) Math.min(
                    balance.getTotalEntitledDays(),                  // cap at total entitled
                    balance.getRemainingDays() + rejectedDays        // add back the days
            );
            balance.setRemainingDays(restoredRemaining);
        }

        leaveBalanceRepo.save(balance);
    }
}
