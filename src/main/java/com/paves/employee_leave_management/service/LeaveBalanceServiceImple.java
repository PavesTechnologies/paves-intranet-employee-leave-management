package com.paves.employee_leave_management.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paves.employee_leave_management.audit.Auditable;
import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.dto.AllPeopleLeaveBalance;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.AccrualFrequency;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.globalExceptionHandler.EmployeeExceptionHandler;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.*;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveBalanceServiceImple implements LeaveBalanceServiceInterface {

    @Autowired
    ElasticsearchClient client;

    @Autowired
    LeaveBalanceDAO leaveBalanceDao;

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceRepo leaveBalanceRepo;

    @Autowired
    GenderBasedRepo genderBasedRepo;

//    @Autowired
//    GenderBasedLeaveBalancesRepo genderBasedLeaveBalancesRepo;


    @Autowired
    EmployeeRepo employeeRepo;

    @Autowired
    HolidaysServiceInterface holidayService;

    @Autowired
    private GenderBasedLeaveBalancesRepo genderBasedLeaveBalancesRepo;

    @Override
    public void createLeaveBalanceForNewEmployee(String empId) {
        Employee emp = employeeRepo.findById(empId).orElseThrow(() -> new EmployeeExceptionHandler("Employee not found: " + empId));

        int currentYear = LocalDate.now().getYear();
        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();
//        List<GenderBasedLeave> genderBasedLeaveTypes = genderBasedRepo.findAll();
        LocalDate onboardingDate = LocalDate.now();
        LocalDate hireDate = emp.getHireDate();
        List<LeaveBalance> balances = new ArrayList<>();



        for (LeaveType lt : leaveTypes) {
            if(lt.getActive().equals(true)) {
                if (leaveBalanceRepo.findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear).isPresent()) {
                    continue;
                }
                if (emp.getGender() != null) {
                    createGenderBasedLeaveBalance(emp,currentYear);
                }
                if(lt.getLeaveName().equals(LeaveTypesEnum.PATERNITY_LEAVE.toString()) || lt.getLeaveName().equals(LeaveTypesEnum.MATERNITY_LEAVE.toString())){
                    continue;
                }

                double accruedLeaves = 0;
                double totalLeaves = 0;
                double carriedForward = 0;
                double usedLeaves = 0;

                if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.SICK_LEAVE.toString())) {
                    LocalDate accrualStart = (hireDate.getYear() < currentYear)
                            ? LocalDate.of(currentYear, 1, 1)
                            : hireDate;
                    accruedLeaves = getAccruedLeaves(accrualStart, onboardingDate, lt.getAccrualRate(), lt.getEffectiveStartDate());
//                totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                    int currYear = Year.now().getValue();

                    int hireYear = hireDate.getYear();
                    int effectiveYear = lt.getEffectiveStartDate().getYear();

// CASE 1: Leave type effective start is after hire date's year
// Employee cannot accrue before effectiveStartDate anyway
                    LocalDate accrualStartDate = hireDate.isAfter(lt.getEffectiveStartDate())
                            ? hireDate
                            : lt.getEffectiveStartDate();

// Now choose the later date between (hireDate, effectiveStartDate)
                    int startMonth = accrualStartDate.getMonthValue();
                    int startDay = accrualStartDate.getDayOfMonth();

// CASE 2: If joining mid-month (after 15), don’t count that month
                    if (startDay > 15) {
                        startMonth += 1;
                    }

                    int monthsLeft = 12 - startMonth + 1; // inclusive count

// Prevent negative values
                    monthsLeft = Math.max(monthsLeft, 0);

// Finally, calculate total leaves
                    totalLeaves = monthsLeft * lt.getAccrualRate();


                } else if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString())) {
                    LocalDate accrualStart = (hireDate.getYear() < currentYear)
                            ? LocalDate.of(currentYear, 1, 1)
                            : hireDate;
                    accruedLeaves = getAccruedLeaves(accrualStart, onboardingDate, lt.getAccrualRate(), lt.getEffectiveStartDate());

                    carriedForward = calculateEarnedLeaveCarryForward(hireDate, currentYear, lt);
                    int currYear = Year.now().getValue();

                    int hireYear = hireDate.getYear();
                    int effectiveYear = lt.getEffectiveStartDate().getYear();

// CASE 1: Leave type effective start is after hire date's year
// Employee cannot accrue before effectiveStartDate anyway
                    LocalDate accrualStartDate = hireDate.isAfter(lt.getEffectiveStartDate())
                            ? hireDate
                            : lt.getEffectiveStartDate();

// Now choose the later date between (hireDate, effectiveStartDate)
                    int startMonth = accrualStartDate.getMonthValue();
                    int startDay = accrualStartDate.getDayOfMonth();

// CASE 2: If joining mid-month (after 15), don’t count that month
                    if (startDay > 15) {
                        startMonth += 1;
                    }

                    int monthsLeft = 12 - startMonth + 1; // inclusive count

// Prevent negative values
                    monthsLeft = Math.max(monthsLeft, 0);

// Finally, calculate total leaves
                    totalLeaves = monthsLeft * lt.getAccrualRate();

                }
//                else if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.PATERNITY_LEAVE.toString())) {
//                    accruedLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
//                    totalLeaves = accruedLeaves;
//                } else if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.MATERNITY_LEAVE.toString())) {
//                    accruedLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
//                    totalLeaves = accruedLeaves;
//                }
            else {
                    totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                    accruedLeaves = 0;
                }

                double remainingLeaves = Math.max(0, (accruedLeaves + carriedForward) - usedLeaves);


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
                balances.add(balance);
            }
        }
        leaveBalanceRepo.saveAll(balances);
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

    private double getAccruedLeaves(LocalDate startDate, LocalDate endDate, double ratePerMonth, LocalDate effectiveStartDate) {
        if (startDate.isAfter(endDate) || startDate.isBefore(effectiveStartDate))
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

    private double calculateEarnedLeaveCarryForward(LocalDate hireDate, int currentYear, LeaveType lt) {
        if(lt.getEffectiveStartDate().isAfter(hireDate)){
            return 0;
        }
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

    public void createGenderBasedLeaveBalance(Employee emp, int year){
        List<GenderBasedLeave> leaveTypes = genderBasedRepo.findAll();
        List<GenderBasedLeaveBalance> balance = new ArrayList<>();
        int totalLeaves = 0;


        for(GenderBasedLeave lt : leaveTypes){
            if(lt.getActive().equals(true)) {
                if (genderBasedLeaveBalancesRepo.findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(emp.getEmployeeId(), lt.getLeaveTypeId(), year).isPresent()) {
                    continue;
                }
                if (emp.getGender() != null) {
                    if (emp.getGender().equalsIgnoreCase("male") && lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.MATERNITY_LEAVE.toString()))
                        continue;
                    if (emp.getGender().equalsIgnoreCase("female") && lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.PATERNITY_LEAVE.toString()))
                        continue;
                }

                if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.PATERNITY_LEAVE.toString())) {
                      totalLeaves = lt.getMaxLeaveDays() != null ? lt.getMaxLeaveDays() : 0;
                } else if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.MATERNITY_LEAVE.toString())) {
                    totalLeaves = lt.getMaxLeaveDays() != null ? lt.getMaxLeaveDays() : 0;
                }
            GenderBasedLeaveBalance bal = new GenderBasedLeaveBalance();
                bal.setTotalEntitledDays(totalLeaves);
                bal.setCreatedAt(LocalDateTime.now());
                bal.setEmployeeId(emp.getEmployeeId());
                bal.setYear(year);
                bal.setTimesUsed(0);
                bal.setUpdatedAt(null);
                bal.setLeaveType(lt);
                genderBasedLeaveBalancesRepo.save(bal);
            }
        }

    }


    @Override
    public void processAccrualForLeaveType() {
        List<LeaveType> types = leaveTypeRepo.findAll();
        for (LeaveType type : types) {
            AccrualFrequency frequency = AccrualFrequency.valueOf(type.getAccrualFrequency().toString().toUpperCase());
            LocalDate today = LocalDate.now();

            if(type.getActive() == false)
                continue;

            switch (frequency) {

                case DAILY:
                    runMonthlyAccrual(type);
                    break;

                case WEEKLY:
                    if (today.getDayOfWeek().getValue() == 1) { // Monday
                        runMonthlyAccrual(type);
                    }
                    break;

                case FORTNIGHTLY:
                    if (today.getDayOfMonth() == 1 || today.getDayOfMonth() == 15) {
                        runMonthlyAccrual(type);
                    }
                    break;

                case MONTHLY:
                    if (today.getDayOfMonth() == 1) {
                        runMonthlyAccrual(type);   // ← calls your exact monthly logic
                    }
                    break;

                case QUARTERLY:
                    if (today.getDayOfMonth() == 1 &&
                            (today.getMonthValue() == 1 ||
                                    today.getMonthValue() == 4 ||
                                    today.getMonthValue() == 7 ||
                                    today.getMonthValue() == 10)) {

                        runMonthlyAccrual(type);
                    }
                    break;

                case YEARLY:
                    if (today.getMonthValue() == 1 && today.getDayOfMonth() == 1) {
                        runMonthlyAccrual(type);   // ← calls your exact yearly logic
                    }
                    break;
                case NONE:
                    break;
            }
        }
    }

    @Override
    public void runMonthlyAccrual(LeaveType type) {

        // Only process leave balances for THIS specific leave type this year
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() == 1 && today.getDayOfMonth() == 1 && type.getMaxCarryForward() > 0) {
            runYearlyAccrual();// ← calls your exact yearly logic
        }
        List<LeaveBalance> balances =
                leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                        today.getYear(),
                        type.getLeaveTypeId()
                );

        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
        }
        LocalDate now = LocalDate.now();
        for (LeaveBalance balance : balances) {
            if(balance.getEmployee().getHireDate().isAfter(now))
            {
                continue;
            }
            Employee emp = balance.getEmployee();
            LeaveType lt = balance.getLeaveType(); // dynamic
            LocalDate hireDate = emp.getHireDate();
            LocalDate accrualDate = balance.getLastAccrualDate();

            // ---- DYNAMIC Monthly Rules ----
            double accrualRate = lt.getAccrualRate() != null ? lt.getAccrualRate() : 0;

            if (accrualRate > 0) {
                balance.setAccruedLeaves(balance.getAccruedLeaves() + accrualRate);
                balance.updateRemainingLeaves();
                balance.setLastAccrualDate(now);
            }
        }
        leaveBalanceRepo.saveAll(balances);
    }



    @Override
    public void runYearlyAccrual() {

        List<LeaveBalance> balances =
                leaveBalanceRepo.findAllByYear(
                        LocalDate.now().getYear()
                );

        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
        }

        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();
        for (LeaveType type : leaveTypes) {
            for (LeaveBalance balance : balances) {

                LeaveBalance newbalance = new LeaveBalance();
                newbalance.setEmployee(balance.getEmployee());
                newbalance.setLeaveType(balance.getLeaveType());

                double unused = balance.getRemainingLeaves();
                double carryForward = balance.getCarriedForward();

                // DYNAMIC RULES FROM LeaveType (NOT HARDCODED!)
                double maxCFPerYear = type.getMaxCarryForwardPerYear() != null ? type.getMaxCarryForwardPerYear() : 0;
                double maxTotalCF = type.getMaxCarryForward() != null ? type.getMaxCarryForward() : 0;
                double maxYearLeaves = type.getMaxDaysPerYear() != null ? type.getMaxDaysPerYear() : 0;

                // core carry-forward logic (DYNAMIC, not tied to leaveName)
                double forward;

                if (unused >= carryForward) {
                    unused = unused - carryForward;
                    forward = Math.min(maxCFPerYear, unused);
                    carryForward = Math.min(maxTotalCF, carryForward + forward);
                } else {
                    forward = Math.min(maxCFPerYear, unused);
                    carryForward = Math.min(maxTotalCF, forward);
                }

                newbalance.setCarriedForward(carryForward);
                newbalance.setExpiredLeaves(unused - forward);
                newbalance.setTotalLeaves(maxYearLeaves);
                newbalance.setAccruedLeaves(0);

                newbalance.setYear(balance.getYear() + 1);
                newbalance.setLastAccrualDate(LocalDate.now());
                newbalance.setUsedLeaves(0);
                newbalance.setEncashedLeaves(0);
                newbalance.updateRemainingLeaves();
                leaveBalanceRepo.save(newbalance);
            }
        }
        holidayService.deleteHolidaysThreeYearsAgo();
    }




    @Override
    public void processYearEndCarryForward() {
//        List<LeaveBalance> balances = leaveBalanceRepo.findAllByYear(LocalDate.now().getYear() - 1);
//        if (balances.isEmpty()) {
//            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
//        }
//
//        for (LeaveBalance balance : balances) {
//            LeaveBalance newbalance = new LeaveBalance();
//            newbalance.setEmployee(balance.getEmployee());
//            newbalance.setLeaveType(balance.getLeaveType());
//
//            String name = balance.getLeaveType().getLeaveName();
//            double unused = balance.getRemainingLeaves();
//            double carryForward = balance.getCarriedForward();
//
//
//            switch (name) {
//                case "EARNED_LEAVE":
//                    double forward;
//                    if (unused >= carryForward) {
//                        unused = unused - carryForward;
//                        forward = Math.min(balance.getLeaveType().getMaxCarryForwardPerYear(), unused);
//                        carryForward = Math.min(balance.getLeaveType().getMaxCarryForward(), carryForward + forward);
//                    } else {
//                        forward = Math.min(balance.getLeaveType().getMaxCarryForwardPerYear(), unused);
//                        carryForward = Math.min(balance.getLeaveType().getMaxCarryForward(), forward);
//                    }
//                    newbalance.setCarriedForward(carryForward);
//                    newbalance.setExpiredLeaves(unused - forward);
//                    newbalance.setTotalLeaves(
//                            (balance.getLeaveType().getMaxDaysPerYear() != null ? balance.getLeaveType().getMaxDaysPerYear() : 0)
//                    );
//                    newbalance.setAccruedLeaves(0);
//                    break;
//                case "SICK_LEAVE":
//                    double forwardSick;
//                    if (unused >= carryForward) {
//                        unused = unused - carryForward;
//                        forwardSick = Math.min(balance.getLeaveType().getMaxCarryForwardPerYear(), unused);
//                        carryForward = Math.min(balance.getLeaveType().getMaxCarryForward(), carryForward + forwardSick);
//                    } else {
//                        forwardSick = Math.min(balance.getLeaveType().getMaxCarryForwardPerYear(), unused);
//                        carryForward = Math.min(balance.getLeaveType().getMaxCarryForward(), forwardSick);
//                    }
//                    newbalance.setCarriedForward(carryForward);
//                    newbalance.setExpiredLeaves(unused - forwardSick);
//                    newbalance.setTotalLeaves(
//                            (balance.getLeaveType().getMaxDaysPerYear() != null ? balance.getLeaveType().getMaxDaysPerYear() : 0)
//                    );
//                    newbalance.setAccruedLeaves(0);
//                    break;
//                default:
//                    newbalance.setCarriedForward(0);
//                    newbalance.setExpiredLeaves(unused);
//            }
//            newbalance.setYear(balance.getYear() + 1);
//            newbalance.setLastAccrualDate(LocalDate.now());
//            newbalance.setUsedLeaves(0);
//            newbalance.setEncashedLeaves(0);
//            newbalance.updateRemainingLeaves();
//            leaveBalanceDao.save(newbalance);
//        }
//        holidayService.deleteHolidaysThreeYearsAgo();
    }

    @Override
    public void triggerMonthlyLeaveAccrual() {
//        List<LeaveBalance> balances = leaveBalanceRepo.findAllByYear(LocalDate.now().getYear());
//        if (balances.isEmpty()) {
//            throw new LeaveBalanceExceptionHandler("No Leave Balances found");
//        }
//        LocalDate now = LocalDate.now();
//
//        if (now.getDayOfMonth() != 1)
//            throw new LeaveBalanceExceptionHandler("Accrual can only be triggered on the first day of the month");
//
//        for (LeaveBalance balance : balances) {
//            Employee emp = balance.getEmployee();
//            LeaveType type = balance.getLeaveType();
//            LocalDate hireDate = emp.getHireDate();
//            LocalDate accrualDate = balance.getLastAccrualDate();
//
//            if (hireDate.isAfter(now.withDayOfMonth(1))) continue;
//
//            if (accrualDate != null &&
//                    accrualDate.getMonth() == now.getMonth() &&
//                    accrualDate.getYear() == now.getYear()) {
//                continue;
//            }
//
//            double accrual = 0;
//
//            if (type.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.SICK_LEAVE.toString())) {
//                accrual = type.getAccrualRate() != null ? type.getAccrualRate() : 0;
//            }
//
//            if (type.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString())) {
//                accrual = type.getAccrualRate() != null ? type.getAccrualRate() : 0;
//                ;
//            }
//
//            if (accrual > 0) {
//                balance.setAccruedLeaves(balance.getAccruedLeaves() + accrual);
//                balance.updateRemainingLeaves();
//                balance.setLastAccrualDate(now);
//                leaveBalanceDao.save(balance);
//            }
//        }
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
        java.time.Year currentYear = java.time.Year.now();
        List<LeaveBalance> filteredBalance = balance
                .stream()
                .filter(b -> b.getYear() == currentYear.getValue())
                .collect(Collectors.toList());
        return new ResponseEntity<>(filteredBalance, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<AllPeopleLeaveBalance>> getAllLeaveBalanceByYear(Integer year) {

        int currentYear = LocalDate.now().getYear();

        List<LeaveBalance> regularLeaveBalance =
                leaveBalanceRepo.findAllByYear(year);

        List<GenderBasedLeaveBalance> genderBasedLeaveBalances =
                genderBasedLeaveBalancesRepo.findAllByYear(year);

        List<AllPeopleLeaveBalance> allPeopleLeaveBalance = new ArrayList<>();

        // 🔹 Regular leave balances
        for (LeaveBalance leaveBalance : regularLeaveBalance) {

            AllPeopleLeaveBalance dto = new AllPeopleLeaveBalance();  // ✅ NEW object each iteration

            dto.setRemainingLeaves(leaveBalance.getRemainingLeaves());
            dto.setEmployeeName(leaveBalance.getEmployee().getFirstName() + " " +
                    leaveBalance.getEmployee().getLastName());
            dto.setEmployeeId(leaveBalance.getEmployee().getEmployeeId());
            dto.setLeaveTypeId(leaveBalance.getLeaveType().getLeaveTypeId());
            dto.setLeaveTypeName(leaveBalance.getLeaveType().getLeaveName());
            dto.setYear(leaveBalance.getYear());
            dto.setGender(leaveBalance.getEmployee().getGender());

            allPeopleLeaveBalance.add(dto);
        }

        // 🔹 Gender-based leave balances
        for (GenderBasedLeaveBalance leaveBalance : genderBasedLeaveBalances) {

            AllPeopleLeaveBalance dto = new AllPeopleLeaveBalance(); // ✅ NEW object each iteration

            dto.setRemainingLeaves(leaveBalance.getRemainingDays());
            dto.setEmployeeId(leaveBalance.getEmployeeId());
            dto.setEmployeeName(""); // fill if needed
            dto.setLeaveTypeId(leaveBalance.getLeaveType().getLeaveTypeId());
            dto.setLeaveTypeName(leaveBalance.getLeaveType().getLeaveName());
            dto.setYear(leaveBalance.getYear());
            dto.setGender(leaveBalance.getLeaveType().getGender());

            allPeopleLeaveBalance.add(dto);
        }

        return new ResponseEntity<>(allPeopleLeaveBalance, HttpStatus.OK);
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
    public ResponseEntity<List<LeaveBalance>> findByEmployeeIdAndYear(String employeeId, int year) {
        List<LeaveBalance> balance = leaveBalanceDao.findByEmployeeIdAndYear(employeeId,year);
        if (balance.isEmpty()) {
            return null;
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


    // without Audit
    @Auditable
    @Transactional
    @Override
    public ResponseEntity<String> updateLeaveBalancesFromHr(LeaveBalanceUpdateRequest request) {
        for (LeaveBalanceUpdateRequest.BalanceUpdate update : request.getBalances()) {
            if(update.getLeaveTypeId() == "L-ML" || update.getLeaveTypeId() == "L-PL"){
                GenderBasedLeaveBalance balance = genderBasedLeaveBalancesRepo.findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                        request.getEmployeeId(),
                        update.getLeaveTypeId(),
                        update.getYear()
                ).get();
            }
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


//    @Override
//    public ResponseEntity<String> updateLeaveBalancesFromHr(LeaveBalanceUpdateRequest request) {
//        for (LeaveBalanceUpdateRequest.BalanceUpdate update : request.getBalances()) {
//            LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
//                    request.getEmployeeId(),
//                    update.getLeaveTypeId(),
//                    update.getYear()
//            );
//
//            if (balance == null) {
//                throw new RuntimeException(
//                        "Leave Balance not found for employeeId: " + request.getEmployeeId() +
//                                ", leaveTypeId: " + update.getLeaveTypeId() +
//                                ", year: " + update.getYear()
//                );
//            }
//
//            // ✅ Capture old value (only the field you care about, or full object)
//            double oldRemaining = balance.getRemainingLeaves();
//
//            // ✅ Update with new value
//            balance.setRemainingLeaves(update.getRemainingLeaves());
//            LeaveBalance updatedBalance = leaveBalanceRepo.save(balance);
//
//            // ✅ Log the change (assuming you inject AuditService in this class)
//            auditLogService.logAudit(
//                    "UPDATE_LEAVE_BALANCE",
//                    "LeaveBalance",
//                    balance.getBalanceId(),                     // entityId
//                    request.getPerformedBy(),            // HR username/employeeId → include in request or extract from JWT
//                    oldRemaining,                        // oldValue (just remaining leaves here)
//                    update.getRemainingLeaves().toString(),         // newValue
//                    "HR updated leave balance via bulk update" // reason (optional, or pass from request)
//            );
//        }
//
//        return ResponseEntity.ok("Leave balances updated successfully.");
//    }


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

    //    @Transactional
//    public void createLeaveBalanceForAllEmployees(LeaveType leaveType) {
//        int currentYear = LocalDate.now().getYear();
//        LocalDate today = LocalDate.now();
//
//        // Calculate first accrual date → 1st of next month
//        LocalDate firstAccrualDate = today.plusMonths(1).withDayOfMonth(1);
//
//        List<Employee> employees = employeeRepo.findAll();
//        for (Employee emp : employees) {
//            // Skip if balance already exists
//            if (leaveBalanceRepo.findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
//                    emp.getEmployeeId(), leaveType.getLeaveTypeId(), currentYear).isPresent()) {
//                continue;
//            }
//
//            // Gender-specific validation
//            if (emp.getGender() != null) {
//                if (emp.getGender().equalsIgnoreCase("male") &&
//                        leaveType.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.MATERNITY_LEAVE.toString()))
//                    continue;
//                if (emp.getGender().equalsIgnoreCase("female") &&
//                        leaveType.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.PATERNITY_LEAVE.toString()))
//                    continue;
//            }
//
//            // Initialize values
//            double accruedLeaves = 0;  // Start fresh
//            double totalLeaves = leaveType.getMaxDaysPerYear() != null ? leaveType.getMaxDaysPerYear() : 0;
//
//            // Example: if it’s Sick/Earned leave → accrual from next month
//            if (leaveType.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.SICK_LEAVE.toString())
//                    || leaveType.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString())) {
//                accruedLeaves = 0; // will start accruing from next month
//            } else {
//                accruedLeaves = totalLeaves; // e.g. maternity/paternity → lump sum, still assign full
//            }
//
//            LeaveBalance balance = LeaveBalance.builder()
//                    .employee(emp)
//                    .leaveType(leaveType)
//                    .year(currentYear)
//                    .accruedLeaves(accruedLeaves)
//                    .carriedForward(0)
//                    .encashedLeaves(0)
//                    .expiredLeaves(0.0)
//                    .lastAccrualDate(firstAccrualDate)  // ✅ accrual starts from next month
//                    .usedLeaves(0)
//                    .remainingLeaves(totalLeaves)
//                    .totalLeaves(totalLeaves)
//                    .build();
//
//            leaveBalanceRepo.save(balance);
//        }
    @Transactional
    public void createLeaveBalanceForAllEmployees(LeaveType leaveType) {
        int year = LocalDate.now().getYear();
        LocalDate createdDate = LocalDate.now();

        List<Employee> employees = employeeRepo.findAll();

        List<LeaveBalance> newBalances = employees.stream()
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
                    return leaveBalanceRepo.findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
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
    public List<String> autocomplete(String query) {
        try {
            // Search in leave_balance index
            SearchResponse<LeaveBalance> response = client.search(s -> s
                            .index("leave_balance") // your ES index
                            .size(5) // max 5 suggestions
                            .query(q -> q
                                    .multiMatch(m -> m
                                            .fields("employee.employeeId", "employee.firstName", "employee.lastName")
                                            .query(query)
                                            .fuzziness("AUTO")
                                    )
                            ),
                    LeaveBalance.class
            );

            // Map hits to "E123 - John Doe"
            return response.hits().hits().stream()
                    .map(hit -> {
                        Employee e = hit.source().getEmployee();
                        return e.getEmployeeId() + " - " + e.getFirstName() + " " + e.getLastName();
                    })
                    .distinct()
                    .collect(Collectors.toList());

        } catch (IOException ex) {
            ex.printStackTrace();
            return Collections.emptyList();
        }
    }


    private LeaveBalance buildLeaveBalance(Employee emp, LeaveType lt, LocalDate referenceDate, boolean isNewLeaveType) {
        int currentYear = referenceDate.getYear();
        LocalDate hireDate = emp.getHireDate();

        double accruedLeaves = 0;
        double totalLeaves = 0;
        double carriedForward = 0;
        double usedLeaves = 0;

        if (lt.getAccrualRate() != null && lt.getAccrualRate() > 0) {
            int monthsLeft = isNewLeaveType
                    ? calculateRemainingMonths(referenceDate)
                    : calculateRemainingMonths(hireDate.isAfter(referenceDate) ? hireDate : referenceDate);

            totalLeaves = lt.getAccrualRate() * monthsLeft;
            accruedLeaves = isNewLeaveType ? 0 : getAccruedLeaves(hireDate, referenceDate, lt.getAccrualRate(), lt.getEffectiveStartDate());

            if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString()) && !isNewLeaveType) {
                carriedForward = calculateEarnedLeaveCarryForward(hireDate, currentYear, lt);
            }
        } else {
            totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
            accruedLeaves = totalLeaves;
        }

        double remainingLeaves = Math.max(0, accruedLeaves + carriedForward - usedLeaves);
        LocalDate firstAccrualDate = referenceDate.plusMonths(1).withDayOfMonth(1);

        return LeaveBalance.builder()
                .employee(emp)
                .leaveType(lt)
                .year(currentYear)
                .accruedLeaves(accruedLeaves)
                .carriedForward(carriedForward)
                .encashedLeaves(0)
                .expiredLeaves(0.0)
                .lastAccrualDate(firstAccrualDate)
                .usedLeaves(usedLeaves)
                .remainingLeaves(remainingLeaves)
                .totalLeaves(totalLeaves)
                .build();
    }

    private int calculateRemainingMonths(LocalDate fromDate) {
        int monthsLeft = 12 - fromDate.getMonthValue();
        if (fromDate.getDayOfMonth() < 15) monthsLeft += 1;
        return monthsLeft;
    }

    @Override
    public List<LeaveBalance> searchLeaveBalances(String query) {
        if (query == null || query.isBlank()) {
            return leaveBalanceRepo.findAll();
        }
        return leaveBalanceRepo.searchByEmployee(query);
    }
    
    @Override
    public ResponseEntity<List<LeaveBalance>> findByEmployeeIdAndYear(String employeeId, Integer year) {
        List<LeaveBalance> balances = leaveBalanceRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);
        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No leave balances found for employee ID: " + employeeId + " and year: " + year);
        }
        return new ResponseEntity<>(balances, HttpStatus.OK);
    }

    @Override
    public List<String> autocompleteEmployee(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return leaveBalanceRepo.autocompleteEmployee(query);
    }

    public List<LeaveBalance> getCurrentYearBalances(String employeeId) {
        int currentYear = java.time.Year.now().getValue();
        return leaveBalanceDao.findByEmployeeIdAndYear(employeeId, currentYear);
    }
}

