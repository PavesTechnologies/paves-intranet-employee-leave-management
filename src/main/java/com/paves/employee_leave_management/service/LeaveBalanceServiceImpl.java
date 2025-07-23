package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
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

@Service
@RequiredArgsConstructor
public class LeaveBalanceServiceImpl implements LeaveBalanceServiceInterface {

    @Autowired
    LeaveBalanceDAO leaveBalanceDao;

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

    @Autowired
    EmployeeRepo employeeRepo;

    @Override
    public void createLeaveBalanceForNewEmployee(String EmpId) {
        Employee emp = employeeRepo.findById(EmpId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + EmpId));
        int currentYear = LocalDate.now().getYear();
        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();

        for (LeaveType lt : leaveTypes) {
            if (leaveBalanceDao.existsByEmployeeIdAndLeaveTypeIdAndYear(emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear))
                continue;

            if(emp.getGender().equalsIgnoreCase("male") && lt.getLeaveName().equalsIgnoreCase("Maternity Leave"))
                continue;

            if(emp.getGender().equalsIgnoreCase("female") && lt.getLeaveName().equalsIgnoreCase("Paternity Leave"))
                continue;

            int monthsEligible = getEligibleMonths(emp.getHireDate(), currentYear);
            double totalLeaves = calculateTotalLeaves(lt, monthsEligible);
            double accruedLeaves = 0;

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
        LocalDate current = hireDate.getYear() == year ? hireDate : LocalDate.of(year, 1, 1);
        int months = 0;
        while (!current.isAfter(LocalDate.of(year, 12, 1))) {
            if (current.getDayOfMonth() <= 15) months++;
            current = current.plusMonths(1).withDayOfMonth(1);
        }
        return Math.min(months, 12);
    }

    private double calculateTotalLeaves(LeaveType leaveType, int monthsEligible) {
        String leaveName = leaveType.getLeaveName().toLowerCase();

        if (leaveName.contains("sick")) {
            return monthsEligible * 1.0;
        } else if (leaveName.contains("earned")) {
            return monthsEligible * 1.25;
        } else if ("immediate".equalsIgnoreCase(leaveType.getAccrualFrequency())) {
            return leaveType.getMaxDaysPerYear() != null ? leaveType.getMaxDaysPerYear() : 0;
        } else if (leaveType.getAccrualRate() != null) {
            return leaveType.getAccrualRate().multiply(BigDecimal.valueOf(monthsEligible)).doubleValue();
        } else {
            return leaveType.getMaxDaysPerYear() != null ? leaveType.getMaxDaysPerYear() : 0;
        }
    }

    private int calculateAccruedLeaves(LeaveType leaveType, int monthsEligible) {
        return (int) calculateTotalLeaves(leaveType, monthsEligible);
    }

    @Override
    public void processYearEndCarryForward() {
        List<LeaveBalance> balances = leaveBalanceRepo.findAll();
        for (LeaveBalance balance : balances) {
            String name = balance.getLeaveType().getLeaveName();
            int unused = (int) balance.getRemainingLeaves();
            int carryForward = 0;

            switch (name) {
                case "Earned Leave":
                    carryForward = Math.min(10, unused);
                    carryForward = Math.min(48, carryForward);
                    balance.setCarriedForward(carryForward);
                    balance.setExpiredLeaves(unused - carryForward);
                    break;
                case "Sick Leave":
                    carryForward = unused;
                    balance.setCarriedForward(carryForward);
                    balance.setExpiredLeaves(0);
                    break;
                default:
                    balance.setCarriedForward(0);
                    balance.setExpiredLeaves(unused);
            }

            balance.updateRemainingLeaves();
            leaveBalanceDao.save(balance);
        }
    }
}
