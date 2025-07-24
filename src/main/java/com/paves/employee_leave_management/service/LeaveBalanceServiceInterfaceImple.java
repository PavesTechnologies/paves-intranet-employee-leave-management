package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveBalanceServiceInterfaceImple implements LeaveBalanceServiceInterface {

    @Autowired
    LeaveBalanceDAO leaveBalanceDa0;

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

    @Override
    public void createLeaveBalanceForNewEmployee(Employee emp) {
        int currentYear = LocalDate.now().getYear();
        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();

        for (LeaveType lt : leaveTypes) {
            if (leaveBalanceDa0.existsByEmployeeIdAndLeaveTypeIdAndYear(emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear))
                continue;

            int monthsEligible = getEligibleMonths(emp.getHireDate(), currentYear, lt);
            int totalLeaves = calculateTotalLeaves(lt, monthsEligible);
            int accruedLeaves = calculateAccruedLeaves(lt, monthsEligible);
            int remainingLeaves = accruedLeaves;

            LeaveBalance balance = LeaveBalance.builder()
                    .employee(emp)
                    .leaveType(lt)
                    .year(currentYear)
                    .totalLeaves(totalLeaves)
                    .accruedLeaves(accruedLeaves)
                    .usedLeaves(0)
                    .expiredLeaves(0)
                    .carriedForward(0)
                    .remainingLeaves(remainingLeaves)
                    .encashedLeaves(0)
                    .lastAccrualDate(null)
                    .build();

            leaveBalanceDa0.save(balance);
        }
    }

    private int getEligibleMonths(LocalDate hireDate, int year, LeaveType leaveType) {
        if (hireDate.getYear() > year) return 0;

        LocalDate start = hireDate.getYear() == year ? hireDate : LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 1);

        int months = 0;
        while (!start.isAfter(end)) {
            if (start.getDayOfMonth() <= 15) months++;
            start = start.plusMonths(1).withDayOfMonth(1);
        }

        if ("Earned Leave".equalsIgnoreCase(leaveType.getLeaveName())) {
            if (ChronoUnit.DAYS.between(hireDate, LocalDate.of(year, 1, 1)) < 90) {
                months = Math.max(0, months - 3);
            }
        }

        return Math.min(months, 12);
    }

    private int calculateTotalLeaves(LeaveType leaveType, int monthsEligible) {
        if ("IMMEDIATE".equalsIgnoreCase(leaveType.getAccrualFrequency())) {
            return leaveType.getMaxDaysPerYear() != null ? leaveType.getMaxDaysPerYear() : 0;
        }
        if (leaveType.getAccrualRate() != null) {
            return leaveType.getAccrualRate().multiply(BigDecimal.valueOf(monthsEligible)).intValue();
        }
        return leaveType.getMaxDaysPerYear() != null ? leaveType.getMaxDaysPerYear() : 0;
    }

    private int calculateAccruedLeaves(LeaveType leaveType, int monthsEligible) {
        return calculateTotalLeaves(leaveType, monthsEligible);
    }

    @Override
    public LeaveBalanceDTO getLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        LeaveBalance balance = leaveBalanceRepo
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
        System.out.println("From leave balance service Implementation");
        System.out.println(balance);
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
//                .availableBalance(balance.getAvailableBalance())
                .year(balance.getYear())
                .build();
    }

}
