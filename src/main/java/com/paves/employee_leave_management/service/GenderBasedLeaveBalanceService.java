package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

}
