package com.paves.employee_leave_management.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paves.employee_leave_management.audit.Auditable;
import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.enums.AccrualFrequency;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import com.paves.employee_leave_management.globalExceptionHandler.EmployeeExceptionHandler;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.*;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.utils.ExcelUtil;
import com.paves.employee_leave_management.utils.UtilsMethods;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;




@Service
@RequiredArgsConstructor
@Slf4j
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

    @Autowired
    LeaveRequestRepo leaveRequestRepo;

//    @Autowired
//    GenderBasedLeaveBalancesRepo genderBasedLeaveBalancesRepo;


    @Autowired
    EmployeeRepo employeeRepo;

    @Autowired
    HolidaysServiceInterface holidayService;

    @Autowired
    private GenderBasedLeaveBalancesRepo genderBasedLeaveBalancesRepo;

    private static final int MID_MONTH_THRESHOLD = 15;

    @Override
    public void createLeaveBalanceForNewEmployee(String empId) {
        Employee emp = employeeRepo.findById(empId)
                .orElseThrow(() -> new EmployeeExceptionHandler("Employee not found: " + empId));

        int currentYear = LocalDate.now().getYear();
        LocalDate today = LocalDate.now();
        LocalDate hireDate = emp.getHireDate();

        log.info("Creating leave balances for employee: {} for year: {}", empId, currentYear);

        if (emp.getGender() != null) {
            createGenderBasedLeaveBalance(emp, currentYear);
        }

        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();
        List<LeaveBalance> balances = new ArrayList<>();

        for (LeaveType lt : leaveTypes) {
            if (!Boolean.TRUE.equals(lt.getActive())) continue;

            if (leaveBalanceRepo.findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
                    emp.getEmployeeId(), lt.getLeaveTypeId(), currentYear).isPresent()) {
                log.debug("Balance already exists for employee: {} leaveType: {}", empId, lt.getLeaveName());
                continue;
            }

            String leaveName = lt.getLeaveName();
            if (leaveName.equals(LeaveTypesEnum.MATERNITY_LEAVE.toString())
                    || leaveName.equals(LeaveTypesEnum.PATERNITY_LEAVE.toString())) {
                continue;
            }

            double accruedLeaves = 0;
            double totalLeaves = 0;
            double carriedForward = 0;
            double usedLeaves = 0;

            boolean isAccrualBased = leaveName.equalsIgnoreCase(LeaveTypesEnum.SICK_LEAVE.toString())
                    || leaveName.equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString());

            if (isAccrualBased) {
                // BUG FIX 1: Use effectiveStartDate if hireDate is before it,
                // instead of silently returning 0
                LocalDate accrualStartDate = hireDate.isBefore(lt.getEffectiveStartDate())
                        ? lt.getEffectiveStartDate()
                        : hireDate;

                LocalDate accrualFrom = accrualStartDate.getYear() < currentYear
                        ? LocalDate.of(currentYear, 1, 1)
                        : accrualStartDate;

                accruedLeaves = getAccruedLeaves(accrualFrom, today, lt.getAccrualRate(), lt.getEffectiveStartDate());
                totalLeaves = calculateProRataTotal(accrualStartDate, lt);

                // BUG FIX 2: Apply carry forward logic to ALL accrual-based leave types
                // (Sick Leave + Earned Leave) based on leaveType config
                // Previously only Earned Leave had carry forward
                if (lt.getMaxCarryForward() != null && lt.getMaxCarryForward() > 0) {
                    carriedForward = calculateCarryForward(hireDate, currentYear, lt);
                }

            } else {
                // BUG FIX 3: Null check was already there but added explicit fallback
                totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
                accruedLeaves = 0;
                carriedForward = 0;
            }

            // BUG FIX 4: For non-accrual leaves, remainingLeaves should be based
            // on totalLeaves not accruedLeaves (which is 0 for non-accrual)
            double remainingLeaves = isAccrualBased
                    ? Math.max(0, (accruedLeaves + carriedForward) - usedLeaves)
                    : Math.max(0, totalLeaves - usedLeaves);

            LeaveBalance balance = LeaveBalance.builder()
                    .employee(emp)
                    .leaveType(lt)
                    .year(currentYear)
                    .accruedLeaves(accruedLeaves)
                    .carriedForward(carriedForward)
                    .encashedLeaves(0)
                    .expiredLeaves(0.0)
                    .lastAccrualDate(today)
                    .usedLeaves(usedLeaves)
                    .remainingLeaves(remainingLeaves)
                    .totalLeaves(totalLeaves)
                    .isDeleted(false)
                    .build();

            balances.add(balance);
            log.debug("Prepared balance for employee: {} leaveType: {} accrued: {} carried: {} total: {}",
                    empId, leaveName, accruedLeaves, carriedForward, totalLeaves);
        }

        leaveBalanceRepo.saveAll(balances);
        log.info("Created {} leave balances for employee: {}", balances.size(), empId);
    }

    private double calculateProRataTotal(LocalDate hireDate, LeaveType lt) {
        // BUG FIX 5: Was using raw hireDate without checking effectiveStartDate,
        // so pro-rata could start before the leave type was even active
        LocalDate accrualStartDate = hireDate.isAfter(lt.getEffectiveStartDate())
                ? hireDate
                : lt.getEffectiveStartDate();

        int startMonth = accrualStartDate.getMonthValue();
        if (accrualStartDate.getDayOfMonth() > MID_MONTH_THRESHOLD) {
            startMonth += 1;
        }

        // BUG FIX 6: startMonth could exceed 12 (e.g. hired Dec 20 → startMonth=13)
        // which made monthsLeft negative
        if (startMonth > 12) {
            return 0;
        }

        int monthsLeft = Math.max(0, 12 - startMonth + 1);
        return monthsLeft * lt.getAccrualRate();
    }


    //calculate accrued leaves
//    private double getEarnedLeave(LocalDate startDate, LocalDate endDate, double ratePerMonth) {
//        LocalDate accrualStart = startDate.getDayOfMonth() > 15
//                ? startDate.plusMonths(1).withDayOfMonth(1)
//                : startDate.withDayOfMonth(1);
//        if (accrualStart.isAfter(endDate)) {
//            return 0;
//        }
//        int months = 0;
//        LocalDate iter = accrualStart;
//        while (!iter.isAfter(endDate.withDayOfMonth(1))) {
//            months++;
//            iter = iter.plusMonths(1);
//        }
//        return months * ratePerMonth;
//    }

    private double getAccruedLeaves(LocalDate startDate, LocalDate endDate,
                                    double ratePerMonth, LocalDate effectiveStartDate) {
        // BUG FIX 7: Previously returned 0 if startDate was before effectiveStartDate.
        // Now we clamp startDate to effectiveStartDate instead of discarding entirely
        LocalDate effectiveStart = startDate.isBefore(effectiveStartDate)
                ? effectiveStartDate
                : startDate;

        if (effectiveStart.isAfter(endDate)) return 0;

        LocalDate adjustedStart = effectiveStart.getDayOfMonth() > MID_MONTH_THRESHOLD
                ? effectiveStart.plusMonths(1).withDayOfMonth(1)
                : effectiveStart.withDayOfMonth(1);

        if (adjustedStart.isAfter(endDate)) return 0;

        int months = 0;
        LocalDate iter = adjustedStart;
        while (!iter.isAfter(endDate.withDayOfMonth(1))) {
            months++;
            iter = iter.plusMonths(1);
        }
        return months * ratePerMonth;
    }

    // Unified carry forward — works for any leave type with maxCarryForward configured
    private double calculateCarryForward(LocalDate hireDate, int currentYear, LeaveType lt) {
        // Use the later of hireDate or effectiveStartDate as the true accrual start
        LocalDate accrualStart = hireDate.isBefore(lt.getEffectiveStartDate())
                ? lt.getEffectiveStartDate()
                : hireDate;

        // No prior years to calculate carry forward from
        if (accrualStart.getYear() >= currentYear) return 0;

        double totalCarried = 0;

        for (int year = accrualStart.getYear(); year < currentYear; year++) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            LocalDate effectiveStart = accrualStart.isAfter(yearStart) ? accrualStart : yearStart;

            double yearlyAccrued = getAccruedLeaves(
                    effectiveStart,
                    yearEnd,
                    lt.getAccrualRate(),
                    lt.getEffectiveStartDate()
            );

            // Cap per-year carry forward if configured
            double yearlyCarry = (lt.getMaxCarryForwardPerYear() != null)
                    ? Math.min(yearlyAccrued, lt.getMaxCarryForwardPerYear())
                    : yearlyAccrued;

            totalCarried += yearlyCarry;

            // Stop accumulating once total cap is reached
            if (lt.getMaxCarryForward() != null && totalCarried >= lt.getMaxCarryForward()) {
                return lt.getMaxCarryForward();
            }
        }

        return totalCarried;
    }


    public void createGenderBasedLeaveBalance(Employee emp, int year) {
        List<GenderBasedLeave> leaveTypes = genderBasedRepo.findAll();

        for (GenderBasedLeave lt : leaveTypes) {
            if (!Boolean.TRUE.equals(lt.getActive())) continue;

            if (genderBasedLeaveBalancesRepo.findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                    emp.getEmployeeId(), lt.getLeaveTypeId(), year).isPresent()) {
                continue;
            }

            String leaveName = lt.getLeaveName();
            String gender = emp.getGender();

            // skip mismatched gender leave types
            if (gender != null) {
                if (gender.equalsIgnoreCase("male")
                        && leaveName.equalsIgnoreCase(LeaveTypesEnum.MATERNITY_LEAVE.toString())) continue;
                if (gender.equalsIgnoreCase("female")
                        && leaveName.equalsIgnoreCase(LeaveTypesEnum.PATERNITY_LEAVE.toString())) continue;
            }

            // totalLeaves moved inside loop — was a bug in original
            int totalLeaves = lt.getMaxLeaveDays() != null ? lt.getMaxLeaveDays() : 0;

            GenderBasedLeaveBalance bal = new GenderBasedLeaveBalance();
            bal.setTotalEntitledDays(totalLeaves);
            bal.setCreatedAt(LocalDateTime.now());
            bal.setEmployeeId(emp.getEmployeeId());
            bal.setYear(year);
            bal.setTimesUsed(0);
            bal.setUpdatedAt(null);
            bal.setLeaveType(lt);
            genderBasedLeaveBalancesRepo.save(bal);

            log.debug("Created gender based balance for employee: {} leaveType: {}",
                    emp.getEmployeeId(), leaveName);
        }
    }


    @Override
    @Async
    public void processAccrualForLeaveType() {
        List<LeaveType> types = leaveTypeRepo.findAll();
        LocalDate today = LocalDate.now();

        // Jan 1st: create new year balance records for all employees first.
        // Carry forward values will be updated later via manual trigger.
        boolean isNewYearDay = (today.getMonthValue() == 1 && today.getDayOfMonth() == 1);
        if (isNewYearDay) {
            for (LeaveType type : types) {
                if (!Boolean.TRUE.equals(type.getActive())) continue;

                List<LeaveBalance> balances = leaveBalanceRepo
                        .findAllByYearAndLeaveTypeLeaveTypeId(today.getYear() - 1, type.getLeaveTypeId());

                if (balances.isEmpty()) {
                    log.warn("No previous year balances found for leave type: {} — skipping new year record creation",
                            type.getLeaveName());
                    continue;
                }

                List<LeaveBalance> nextYearBalances = balances.stream()
                        .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                        .map(b -> {
                            LeaveBalance nb = new LeaveBalance();
                            nb.setEmployee(b.getEmployee());
                            nb.setEmployeeId(b.getEmployee().getEmployeeId());
                            nb.setLeaveType(type);
                            nb.setYear(today.getYear());

                            // Reset usage fields for new year
                            nb.setUsedLeaves(0.0);
                            nb.setEncashedLeaves(0);

                            // Credit first month only if before mid-month threshold
                            double firstAccrual = today.getDayOfMonth() < MID_MONTH_THRESHOLD
                                    ? (type.getAccrualRate() != null ? type.getAccrualRate() : 0.0)
                                    : 0.0;
                            nb.setAccruedLeaves(firstAccrual);

                            // Carry forward stays 0 — manual trigger updates this later
                            nb.setCarriedForward(0.0);
                            nb.setExpiredLeaves(0.0);

                            nb.setTotalLeaves(type.getMaxDaysPerYear() != null ? type.getMaxDaysPerYear() : 0.0);
                            nb.setRemainingLeaves(firstAccrual);
                            nb.setLastAccrualDate(today);
                            nb.setIsBlocked(b.getIsBlocked());
                            nb.setBlockId(b.getBlockId());
                            nb.setIsDeleted(false);
                            nb.setLastUpdatedAt(null);
                            nb.setCreateAt(LocalDateTime.now());

                            return nb;
                        })
                        .collect(Collectors.toList());

                leaveBalanceRepo.saveAll(nextYearBalances);
                log.info("New year balance records created for leave type: {} — {} records",
                        type.getLeaveName(), nextYearBalances.size());
            }
            return;
        }

        // Normal accrual for all other days
        for (LeaveType type : types) {
            if (!Boolean.TRUE.equals(type.getActive())) continue;

            try {
                AccrualFrequency frequency = AccrualFrequency
                        .valueOf(type.getAccrualFrequency().toString().toUpperCase());

                switch (frequency) {
                    case DAILY:
                        runMonthlyAccrual(type);
                        break;

                    case WEEKLY:
                        if (today.getDayOfWeek().getValue() == 1) {
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
                            runMonthlyAccrual(type);
                        }
                        break;

                    case QUARTERLY:
                        // Jan 1st is handled above by new year record creation.
                        // Quarterly accrual resumes from Apr 1st onwards.
                        if (today.getDayOfMonth() == 1 &&
                                (today.getMonthValue() == 4 ||
                                        today.getMonthValue() == 7 ||
                                        today.getMonthValue() == 10)) {
                            runMonthlyAccrual(type);
                        }
                        break;

                    case YEARLY:
                    case NONE:
                        break;
                }
            } catch (Exception e) {
                log.error("Accrual failed for leave type: {} — skipping. Error: {}",
                        type.getLeaveName(), e.getMessage(), e);
            }
        }
    }

    @Override
    @Transactional
    @Async
    public void runMonthlyAccrual(LeaveType type) {
        LocalDate today = LocalDate.now();

        List<LeaveBalance> balances = leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                today.getYear(),
                type.getLeaveTypeId()
        );

        if (balances.isEmpty()) {
            log.warn("No leave balances found for leave type: {} year: {} — skipping",
                    type.getLeaveName(), today.getYear());
            return;
        }

        for (LeaveBalance balance : balances) {
            if (Boolean.TRUE.equals(balance.getIsDeleted())) continue;

            if (balance.getEmployee().getHireDate().isAfter(today)) continue;

            double accrualRate = type.getAccrualRate() != null ? type.getAccrualRate() : 0;

            if (accrualRate > 0) {
                balance.setAccruedLeaves(balance.getAccruedLeaves() + accrualRate);
                balance.updateRemainingLeaves();
                balance.setLastAccrualDate(today);
            }
        }

        leaveBalanceRepo.saveAll(balances);
        log.info("Accrual completed for leave type: {} — {} balances processed",
                type.getLeaveName(), balances.size());
    }



    @Override
    @Transactional
    @Async
    public void runYearlyAccrual(LeaveType type) {

        List<LeaveBalance> balances =
                leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                        LocalDate.now().getYear() - 1, type.getLeaveTypeId()
                );

        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found yearly");
        }

            for (LeaveBalance balance : balances) {

                if(balance.getIsDeleted())
                    continue;

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
                newbalance.setIsBlocked(balance.getIsBlocked());
                newbalance.setBlockId(balance.getBlockId());
                leaveBalanceRepo.save(newbalance);
            }
        holidayService.deleteHolidaysThreeYearsAgo();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @Override
    @Async
    public UploadResponse handleAccruedUpload(MultipartFile file, String username) throws IOException {
        List<RowError> errors = new ArrayList<>();
        int processedCount = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
//            int year = LocalDate.now().getYear(); // Or get from a cell/param

            for (Row row : sheet) {
                // Skip Header
                if (row.getRowNum() == 0) continue;

                try {
                    // 1. Extract Data (Assuming: Col 0: EmpID, Col 1: LeaveTypeID, Col 2: Accrued, Col 3: Remaining)
                    String empId = getCellValueAsString(row.getCell(0));
                    String typeId = getCellValueAsString(row.getCell(1));
                    double accrued = row.getCell(2).getNumericCellValue();
                    double remaining = row.getCell(3).getNumericCellValue();
                    double usedLeaves = row.getCell(4).getNumericCellValue();
                    double totalLeaves = row.getCell(5).getNumericCellValue();
                    double carryForward = row.getCell(6).getNumericCellValue();
                    int year = (int) row.getCell(7).getNumericCellValue();

                    // 2. Validate Employee & LeaveType
                    Employee employee = employeeRepo.findById(empId)
                            .orElseThrow(() -> new RuntimeException("Employee not found: " + empId));

                    LeaveType leaveType = leaveTypeRepo.findById(typeId)
                            .orElseThrow(() -> new RuntimeException("Leave Type not found: " + typeId));

                    // 3. Find existing or create new
                    // 1. Try to find existing record
                    Optional<LeaveBalance> existingBalance = leaveBalanceRepo
                            .findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(empId, typeId, year); // ✅ fixed

                    LeaveBalance balance;
                    if (existingBalance.isPresent()) {
                        balance = existingBalance.get(); // has balanceId → JPA will UPDATE
                    } else {
                        balance = new LeaveBalance();
                        balance.setCreateAt(LocalDateTime.now()); // only on new records
                    }

                    balance.setEmployee(employee);
// balance.setEmployeeId(empId); ← REMOVED
                    balance.setLeaveType(leaveType);
                    balance.setAccruedLeaves(accrued);
                    balance.setRemainingLeaves(remaining);
                    balance.setUsedLeaves(usedLeaves);
                    balance.setTotalLeaves(totalLeaves);
                    balance.setCarriedForward(carryForward);
                    balance.setYear(year);
                    balance.setLastAccrualDate(LocalDate.now());
                    balance.setLastUpdatedAt(LocalDateTime.now());

                    leaveBalanceRepo.save(balance);
                    processedCount++;

                } catch (Exception e) {
                    errors.add(new RowError(row.getRowNum() + 1, e.getMessage()));
                }
            }

            // If there are ANY errors, we throw an exception to trigger @Transactional rollback
            if (!errors.isEmpty()) {
                throw new RuntimeException("Validation failed in one or more rows. Transaction rolled back.");
            }
        }

        return UploadResponse.builder()
                .message("Upload successful")
                .processedCount(processedCount)
                .errors(new ArrayList<>())
                .build();
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Leave Balances");

            // 1. Create a Header Style (Bold)
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            // 2. Define Headers
            String[] headers = {
                    "Employee ID",   // Index 0
                    "Leave Type ID", // Index 1
                    "Accrued Leaves",// Index 2
                    "Remaining Leaves", // Index 3
                    "Used Leaves",   // Index 4
                    "Total Leaves",  // Index 5
                    "Carry Forward", // Index 6
                    "Year"           // Index 7
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 3. Add a sample row with CORRECT indices
            Row sampleRow = sheet.createRow(1);
            sampleRow.createCell(0).setCellValue("PAVEMPB0A28"); // Employee ID
            sampleRow.createCell(1).setCellValue("L-SL");        // Leave Type ID
            sampleRow.createCell(2).setCellValue(2.0);           // Accrued
            sampleRow.createCell(3).setCellValue(2.0);           // Remaining
            sampleRow.createCell(4).setCellValue(0.0);           // Used
            sampleRow.createCell(5).setCellValue(10.0);          // Total
            sampleRow.createCell(6).setCellValue(0.0);           // Carry Forward
            sampleRow.createCell(7).setCellValue(2026);          // Year (Numeric)

            // 4. Auto-size columns after adding data
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * READS EXCEL -> RETURNS JSON
     */
    public List<LeaveBalanceDTO> parseExcel(MultipartFile file) throws IOException {
        List<LeaveBalanceDTO> results = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip Header

                results.add(LeaveBalanceDTO.builder()
                        .employeeId(getCellValueAsString(row.getCell(0)))
                        .leaveTypeId(getCellValueAsString(row.getCell(1)))
                        .accruedLeaves(getNumericValue(row.getCell(2)))
                        .remainingLeaves(getNumericValue(row.getCell(3)))
                        .usedLeaves(getNumericValue(row.getCell(4)))
                        .year(LocalDate.now().getYear())
                        .build());
            }
        }
        return results;
    }

    private double getNumericValue(Cell cell) {
        // Check if cell is null to avoid NullPointerException
        if (cell == null) {
            return 0.0;
        }

        // Check if the cell actually contains a number
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }

        // If it's a string that looks like a number, try to parse it
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        return 0.0;
    }



    @Override
    @Async
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
    @Transactional
    public void deleteLeaveBalance(String employeeId) {
        log.info("Deleting leave balances for employee: {}", employeeId);
        
        // Find all leave balances for the employee
        List<LeaveBalance> balances = leaveBalanceRepo.findByEmployeeEmployeeId(employeeId);
        
        if (balances.isEmpty()) {
            log.warn("No leave balances found for employee: {}", employeeId);
            return;
        }
        // Delete all leave balances for the employee
        for (LeaveBalance balance : balances) {
            balance.setIsDeleted(true);
            leaveBalanceRepo.save(balance);
        }
        log.info("Deleted {} leave balances for employee: {}", balances.size(), employeeId);
    }

    @Override
    public List<LeaveBalanceForDashboard> getLeaveBalancesForDashboard(String employeeId, int year) {
        List<LeaveBalance> leaveBalances= leaveBalanceRepo.findByEmployeeEmployeeIdAndYear(employeeId, year);
        if (leaveBalances.isEmpty()){
            return null;
        }
        List<LeaveBalanceForDashboard> leaveBalanceForDashboard = new ArrayList<>();
        for(LeaveBalance leaveBalance: leaveBalances){
            LeaveBalanceForDashboard balances = new LeaveBalanceForDashboard();
            balances.setUsedLeaves(leaveBalance.getUsedLeaves());
            balances.setRemainingBalance(leaveBalance.getRemainingLeaves());
            balances.setLeaveName(UtilsMethods.resolveLeaveLabel(leaveBalance.getLeaveType().getLeaveName()));

            leaveBalanceForDashboard.add(balances);
        }
        return leaveBalanceForDashboard;
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
    public LeaveBalance findByBalanceId(String balanceId) {
        LeaveBalance balance = leaveBalanceDao.findById(balanceId);
        if (balance == null || balance.getIsDeleted()) {
            throw new LeaveBalanceExceptionHandler("Balance not found: " + balanceId);
        }
        return balance;
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
                .filter(b -> b.getYear() == currentYear.getValue() && !b.getIsDeleted())
                .collect(Collectors.toList());
        return new ResponseEntity<>(filteredBalance, HttpStatus.OK);
    }

    @Override
    @Cacheable(value= "employeesLeaveBalances", key="#year")
    public List<AllPeopleLeaveBalance> getAllLeaveBalanceByYear(Integer year) {

        int currentYear = LocalDate.now().getYear();

        List<LeaveBalance> regularLeaveBalance =
                leaveBalanceRepo.findAllByYear(year);

        List<GenderBasedLeaveBalance> genderBasedLeaveBalances =
                genderBasedLeaveBalancesRepo.findAllByYear(year);

        List<AllPeopleLeaveBalance> allPeopleLeaveBalance = new ArrayList<>();

        // 🔹 Regular leave balances
        for (LeaveBalance leaveBalance : regularLeaveBalance) {

            if(leaveBalance.getIsDeleted()){
                continue;
            }

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

            if(leaveBalance.getIsDeleted()){
                continue;
            }
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

        return allPeopleLeaveBalance;
    }


    @Override
    public ResponseEntity<List<LeaveBalance>> findByEmployeeId(String employeeId) {
        List<LeaveBalance> balance = leaveBalanceDao.findByEmployeeId(employeeId);
        if (balance.isEmpty() || balance.stream().anyMatch(b -> b.getIsDeleted())) {
            throw new LeaveBalanceExceptionHandler("Leave Balances not found for employee: " + employeeId);
        }
        return new ResponseEntity<>(balance, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<LeaveBalance>> findByEmployeeIdAndYear(String employeeId, int year) {
        List<LeaveBalance> balance = leaveBalanceDao.findByEmployeeIdAndYear(employeeId,year);
        if (balance.isEmpty() || balance.stream().anyMatch(b -> b.getIsDeleted())) {
            return null;
        }
        return new ResponseEntity<>(balance, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<LeaveBalance>> findByLeaveId(String leaveId) {
        List<LeaveBalance> balance = leaveBalanceDao.findByLeaveId(leaveId);
        if (balance.isEmpty() || balance.stream().anyMatch(b -> b.getIsDeleted())) {
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

        balance.setUsedLeaves(round2(balance.getUsedLeaves() + approvedDays));
        if (!leaveTypeId.equalsIgnoreCase("L-UP")) {
            balance.setRemainingLeaves(round2(balance.getRemainingLeaves() - approvedDays));
        }
        leaveBalanceRepo.save(balance);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public void updateLeaveBalanceAfterRejected(String employeeId, String leaveTypeId, double daysRequested, int year) {
        LeaveBalance balance = leaveBalanceRepo
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
//        if(leaveTypeId.equalsIgnoreCase("L-UP") && balance.getUsedLeaves()>0){
//            balance.setUsedLeaves(balance.getUsedLeaves() - daysRequested);
//        }else{
//        }
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
    @CacheEvict(value = "employeeLeaveBalance", key = "#request.getEmployeeId() + '_' +#request.getYear()")
    public ResponseEntity<String> updateLeaveBalancesFromHr(LeaveBalanceUpdateRequest request) {
        System.out.println("=== Updating balances for employee: " + request.getEmployeeId());

        for (LeaveBalanceUpdateRequest.BalanceUpdate update : request.getBalances()) {
            System.out.println("=== Processing leaveTypeId: " + update.getLeaveTypeId() + ", year: " + update.getYear());

            if (update.getLeaveTypeId().equals("L-ML") || update.getLeaveTypeId().equals("L-PL")) {
                GenderBasedLeaveBalance balance = genderBasedLeaveBalancesRepo
                        .findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                                request.getEmployeeId(),
                                update.getLeaveTypeId(),
                                update.getYear()
                        )
                        .orElseThrow(() -> new RuntimeException(
                                "Gender leave balance not found for employeeId: " + request.getEmployeeId()
                        ));

                System.out.println("=== Found gender balance ID: " + balance.getBalanceId());
                System.out.println("=== Before — remainingDays: " + balance.getRemainingDays());
                balance.setRemainingDays(update.getRemainingLeaves().intValue());
                if (update.getUsedLeaves() != null) balance.setUsedDays(update.getUsedLeaves().intValue());

                GenderBasedLeaveBalance saved = genderBasedLeaveBalancesRepo.save(balance);
                System.out.println("=== After save — ID: " + saved.getBalanceId() + ", remainingDays: " + saved.getRemainingDays());

            } else {
                LeaveBalance balance = leaveBalanceRepo
                        .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                                request.getEmployeeId(),
                                update.getLeaveTypeId(),
                                update.getYear()
                        );

                System.out.println("=== Found regular balance: " + balance);

                if (balance == null) {
                    System.out.println("=== ERROR: Balance is NULL — record not found in DB");
                    throw new RuntimeException(
                            "Leave balance not found for employeeId: " + request.getEmployeeId() +
                                    ", leaveTypeId: " + update.getLeaveTypeId() +
                                    ", year: " + update.getYear()
                    );
                }

                System.out.println("=== Before — remainingLeaves: " + balance.getRemainingLeaves());
                balance.setRemainingLeaves(update.getRemainingLeaves());
                if (update.getUsedLeaves() != null) balance.setUsedLeaves(update.getUsedLeaves());
                if (update.getAccruedLeaves() != null) balance.setAccruedLeaves(update.getAccruedLeaves());

                LeaveBalance saved = leaveBalanceRepo.save(balance);
                System.out.println("=== After save — ID: " + saved.getBalanceId() + ", remainingLeaves: " + saved.getRemainingLeaves());
            }
        }

        System.out.println("=== All balances updated successfully");
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
        if (balance == null || balance.getIsDeleted()) {
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
    @Async
    public void createLeaveBalanceForAllEmployees(LeaveType leaveType) {
        int year = LocalDate.now().getYear();
        LocalDate createdDate = LocalDate.now();

        List<Employee> employees = employeeRepo.findAll();

        List<LeaveBalance> newBalances = employees.stream()
                .filter(emp -> {
                    // BUG FIX 1: Null check on gender before calling equalsIgnoreCase
                    // Previously would throw NullPointerException for employees with no gender set
                    String gender = emp.getGender() != null ? emp.getGender() : "";

                    if (leaveType.getLeaveName().equalsIgnoreCase("MATERNITY_LEAVE")
                            && gender.equalsIgnoreCase("MALE")) {
                        return false;
                    }

                    if (leaveType.getLeaveName().equalsIgnoreCase("PATERNITY_LEAVE")
                            && gender.equalsIgnoreCase("FEMALE")) {
                        return false;
                    }

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

    private LeaveBalance buildLeaveBalance(Employee emp, LeaveType lt, LocalDate referenceDate, boolean isNewLeaveType) {
        int currentYear = referenceDate.getYear();
        LocalDate hireDate = emp.getHireDate();

        double accruedLeaves = 0;
        double totalLeaves = 0;
        double carriedForward = 0;
        double usedLeaves = 0;

        boolean isAccrualBased = lt.getAccrualRate() != null && lt.getAccrualRate() > 0;

        if (isAccrualBased) {
            if (isNewLeaveType) {
                // For new leave types, start accrual from today unless hire date is future
                LocalDate startDate = hireDate.isAfter(referenceDate) ? hireDate : referenceDate;

                totalLeaves = lt.getAccrualRate() * calculateRemainingMonths(startDate);

                // Credit current month only if before mid-month
                accruedLeaves = (referenceDate.getDayOfMonth() < MID_MONTH_THRESHOLD)
                        ? lt.getAccrualRate()
                        : 0;

            } else {
                // BUG FIX 2: Clamp startDate to effectiveStartDate — previously passed
                // raw hireDate which caused getAccruedLeaves to return 0 silently
                LocalDate effectiveDate = lt.getEffectiveStartDate();
                LocalDate accrualStart = hireDate.isBefore(effectiveDate) ? effectiveDate : hireDate;

                // BUG FIX 3: startDate should be the later of accrualStart or referenceDate
                // Previously this was inverted — it used referenceDate when accrualStart
                // was before it, which always resolved to referenceDate, giving 0 months
                LocalDate startDate = accrualStart.isAfter(referenceDate) ? accrualStart : referenceDate;

                totalLeaves = lt.getAccrualRate() * calculateRemainingMonths(startDate);
                accruedLeaves = getAccruedLeaves(accrualStart, referenceDate, lt.getAccrualRate(), lt.getEffectiveStartDate());
            }

            // BUG FIX 4: Apply carry forward to ALL accrual-based leave types
            // that have maxCarryForward configured — not just Earned Leave.
            // This fixes Sick Leave carry forward being skipped entirely.
            if (lt.getMaxCarryForward() != null && lt.getMaxCarryForward() > 0) {
                carriedForward = calculateCarryForward(hireDate, currentYear, lt);
            }

        } else {
            // Fixed leave — full quota available immediately
            totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
            accruedLeaves = totalLeaves;
            // BUG FIX 5: Non-accrual types can also have carry forward (e.g. casual leave)
            // Previously carry forward was hardcoded to 0 for all non-accrual types
            if (lt.getMaxCarryForward() != null && lt.getMaxCarryForward() > 0) {
                carriedForward = calculateCarryForward(hireDate, currentYear, lt);
            }
        }

        // BUG FIX 6: lastAccrualDate logic was tied only to isNewLeaveType flag,
        // ignoring whether the employee is a new hire mid-year vs existing employee.
        // For non-new leave types, lastAccrualDate should reflect the actual last accrual.
        LocalDate lastAccrualDate;
        if (isNewLeaveType) {
            lastAccrualDate = (referenceDate.getDayOfMonth() < MID_MONTH_THRESHOLD)
                    ? referenceDate.withDayOfMonth(1)
                    : referenceDate.plusMonths(1).withDayOfMonth(1);
        } else {
            // For existing leave types, last accrual was the 1st of the current month
            // if past mid-month, otherwise 1st of previous month
            lastAccrualDate = (referenceDate.getDayOfMonth() >= MID_MONTH_THRESHOLD)
                    ? referenceDate.withDayOfMonth(1)
                    : referenceDate.minusMonths(1).withDayOfMonth(1);
        }

        double remainingLeaves = Math.max(0, accruedLeaves + carriedForward - usedLeaves);

        return LeaveBalance.builder()
                .employee(emp)
                .leaveType(lt)
                .year(currentYear)
                .accruedLeaves(accruedLeaves)
                .carriedForward(carriedForward)
                .encashedLeaves(0)
                .expiredLeaves(0.0)
                .lastAccrualDate(lastAccrualDate)
                .usedLeaves(usedLeaves)
                .remainingLeaves(remainingLeaves)
                .totalLeaves(totalLeaves)
                .isDeleted(false)  // BUG FIX 7: was missing isDeleted flag here, present in createLeaveBalanceForNewEmployee
                .build();
    }

    private int calculateRemainingMonths(LocalDate fromDate) {
        // BUG FIX 8: When day > MID_MONTH_THRESHOLD in December,
        // monthsLeft = (12 - 12) = 0, then +0 = 0 → employee gets nothing
        // They should still get credited for December if before mid-month,
        // which the original code handles correctly. But December after mid-month
        // correctly returns 0 — no more months left in the year. This is fine.
        // However: January before mid-month gave 12 - 1 + 1 = 12 months ✓
        // January after mid-month gave 12 - 1 = 11 months ✓
        int monthsLeft = 12 - fromDate.getMonthValue();
        if (fromDate.getDayOfMonth() < MID_MONTH_THRESHOLD) {
            monthsLeft += 1;
        }
        return Math.max(0, monthsLeft);  // Safety guard — ensure never negative
    }

    @Override
    public List<LeaveBalance> searchLeaveBalances(String query, int year) {
        if (query == null || query.isBlank()) {
            return leaveBalanceRepo.findAll();
        }
        return leaveBalanceRepo.searchByEmployee(query, year);
    }
    
    @Override
    public List<LeaveBalance> findByEmployeeIdAndYear(String employeeId, Integer year) {
         return leaveBalanceRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);
    }

    @Override
//    @Cacheable(value = "employeeLeaveBalance", key = "#employeeId + '-' + #year")
    public EmployeeLeaveBalance findByEmployeeIdAndYearPerEmployee(String employeeId, Integer year){
                List<LeaveBalance> regular = leaveBalanceRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);
                List<GenderBasedLeaveBalance> genderBasedLeaveBalances = genderBasedLeaveBalancesRepo.findByEmployeeIdAndYear(employeeId, year);

                EmployeeLeaveBalance employeeLeaveBalance = new EmployeeLeaveBalance();
                employeeLeaveBalance.setGenderBasedLeaveBalances(genderBasedLeaveBalances);
                employeeLeaveBalance.setRegular(regular);
                return employeeLeaveBalance;
    }

    @Override
    @Cacheable(value = "employeeLeaveBalance", key = "#employeeId + '_' + #year")
    public EmployeeLeaveBalanceForDropdown getLeaveBalanceForDropdown(String employeeId, Integer year) {

        System.out.println("🔥 DB HIT - Leave Balance");

        List<LeaveBalance> regular =
                leaveBalanceRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);

        List<GenderBasedLeaveBalance> genderBased =
                genderBasedLeaveBalancesRepo.findByEmployeeIdAndYear(employeeId, year);


        List<LeaveBalanceRemainingForLeaveDropDown> regularList = regular.stream()
                .map(lb -> {
                    LeaveBalanceRemainingForLeaveDropDown dto = new LeaveBalanceRemainingForLeaveDropDown();
                    dto.setLeaveName(lb.getLeaveType().getLeaveName());
                    dto.setRemainingLeaves(lb.getRemainingLeaves());
                    dto.setLeaveTypeId(lb.getLeaveType().getLeaveTypeId());
                    dto.setAllowHalfDay(lb.getLeaveType().getAllowHalfDay());
                    return dto;
                })
                .toList();


        List<LeaveBalanceRemainingForLeaveDropDown> genderList = genderBased.stream()
                .map(gb -> {
                    LeaveBalanceRemainingForLeaveDropDown dto = new LeaveBalanceRemainingForLeaveDropDown();
                    dto.setLeaveName(gb.getLeaveType().getLeaveName());
                    dto.setRemainingLeaves(gb.getRemainingDays() * 1.0);
                    dto.setLeaveTypeId(gb.getLeaveType().getLeaveTypeId());
                    dto.setAllowHalfDay(false);
                    return dto;
                })
                .toList();

        EmployeeLeaveBalanceForDropdown response = new EmployeeLeaveBalanceForDropdown();
        response.setRegular(regularList);
        response.setGenderBasedLeaveBalances(genderList);

        return response;
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

    @Transactional
    @Override
    @CacheEvict(value = "employeeLeaveBalance", allEntries = true)
    public void processCarryForward(int year) {

        List<LeaveType> leaveTypes = leaveTypeRepo.findAll();

        for (LeaveType leaveType : leaveTypes) {

            List<LeaveBalance> currentYearBalances =
                    leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                            year, leaveType.getLeaveTypeId()
                    );

            List<LeaveBalance> prevYearBalances =
                        leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                            year - 1, leaveType.getLeaveTypeId()
                    );

            List<LeaveBalance> nextYearBalances =
                    leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                            year + 1, leaveType.getLeaveTypeId()
                    );

            Map<String, LeaveBalance> prevYearMap = prevYearBalances.stream()
                    .collect(Collectors.toMap(lb -> lb.getEmployee().getEmployeeId(), lb -> lb));

            Map<String, LeaveBalance> nextYearMap = nextYearBalances.stream()
                    .collect(Collectors.toMap(lb -> lb.getEmployee().getEmployeeId(), lb -> lb));

            List<LeaveBalance> toSave = Collections.synchronizedList(new ArrayList<>());

            currentYearBalances.parallelStream().forEach(current -> {

                String empId = current.getEmployee().getEmployeeId();

                LeaveBalance prevYear = prevYearMap.get(empId);
                LeaveBalance nextYear = nextYearMap.get(empId);

                double prevCarry = (prevYear != null) ? prevYear.getCarriedForward() : 0.0;

                double newCarryForward = 0.0;

                if (leaveType.getMaxCarryForwardPerYear() > 0) {

                    double eligibleLeaves = Math.min(
                            current.getRemainingLeaves(),
                            leaveType.getMaxCarryForwardPerYear()
                    );

                    double availableCapacity =
                            leaveType.getMaxCarryForward() - prevCarry;

                    double leavesToAdd = Math.max(0,
                            Math.min(eligibleLeaves, availableCapacity)
                    );

                    newCarryForward = prevCarry + leavesToAdd;
                }

                LeaveBalance target;

                if (nextYear != null) {
                    target = nextYear;
                } else {
                    target = new LeaveBalance();
                    target.setEmployee(current.getEmployee());
                    target.setLeaveType(leaveType);
                    target.setYear(year + 1);
                    target.setTotalLeaves(leaveType.getMaxDaysPerYear());
                    target.setUsedLeaves(0.0);
                    target.setCreateAt(LocalDateTime.now());
                    target.setLastAccrualDate(LocalDate.now());
                    target.setAccruedLeaves(leaveType.getAccrualRate());
                }

                target.setCarriedForward(newCarryForward);
                target.setRemainingLeaves(
                        leaveType.getAccrualRate() + newCarryForward
                );

                toSave.add(target);
            });

            leaveBalanceRepo.saveAll(toSave);
        }

    }



    public ResponseEntity<Map<String, Object>> getAllLeaveBalanceByYear(
            Integer year, int page, int size, String employeeId, boolean isAdmin) {

        System.out.println("employee Id and admin: "+employeeId+" "+isAdmin);

        List<LeaveBalance> regularLeaveBalances;
        List<GenderBasedLeaveBalance> genderBasedLeaveBalances;

        if (isAdmin) {
            regularLeaveBalances = leaveBalanceRepo
                    .findAllByYearAndNotDeleted(year);
            genderBasedLeaveBalances = genderBasedLeaveBalancesRepo
                    .findAllByYearAndNotDeleted(year);
        } else {
            regularLeaveBalances = leaveBalanceRepo
                    .findAllByYearAndHrId(year, employeeId);
            genderBasedLeaveBalances = genderBasedLeaveBalancesRepo
                    .findAllByYearAndHrId(year, employeeId);
        }

        System.out.println("regularLeaveBalances: " + regularLeaveBalances);
        System.out.println("genderBasedLeaveBalances: " + genderBasedLeaveBalances);

        // rest of your existing code stays exactly the same
        Map<String, EmployeeLeaveBalanceDTO> employeeMap = new LinkedHashMap<>();

        for (LeaveBalance lb : regularLeaveBalances) {
            String empId = lb.getEmployee().getEmployeeId();
            employeeMap.computeIfAbsent(empId, k -> {
                EmployeeLeaveBalanceDTO dto = new EmployeeLeaveBalanceDTO();
                dto.setEmployeeId(empId);
                dto.setEmployeeName(lb.getEmployee().getFirstName() + " " + lb.getEmployee().getLastName());
                dto.setGender(lb.getEmployee().getGender());
                dto.setYear(lb.getYear());
                dto.setLeaves(new ArrayList<>());
                return dto;
            });
            LeaveDetail detail = new LeaveDetail();
            detail.setLeaveTypeId(lb.getLeaveType().getLeaveTypeId());
            detail.setLeaveTypeName(lb.getLeaveType().getLeaveName());
            detail.setRemainingLeaves(lb.getRemainingLeaves());
            detail.setTotalLeaves(lb.getTotalLeaves());
            employeeMap.get(empId).getLeaves().add(detail);
        }

        for (GenderBasedLeaveBalance lb : genderBasedLeaveBalances) {
            String empId = lb.getEmployeeId();
            employeeMap.computeIfAbsent(empId, k -> {
                EmployeeLeaveBalanceDTO dto = new EmployeeLeaveBalanceDTO();
                dto.setEmployeeId(empId);
                dto.setEmployeeName("");
                dto.setGender(lb.getLeaveType().getGender());
                dto.setYear(lb.getYear());
                dto.setLeaves(new ArrayList<>());
                return dto;
            });
            LeaveDetail detail = new LeaveDetail();
            detail.setLeaveTypeId(lb.getLeaveType().getLeaveTypeId());
            detail.setLeaveTypeName(lb.getLeaveType().getLeaveName());
            detail.setRemainingLeaves(lb.getRemainingDays());
            detail.setTotalLeaves(lb.getTotalEntitledDays());
            employeeMap.get(empId).getLeaves().add(detail);
        }

        List<EmployeeLeaveBalanceDTO> allEmployees = new ArrayList<>(employeeMap.values());
        int totalItems = allEmployees.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<EmployeeLeaveBalanceDTO> pageData = (fromIndex >= totalItems)
                ? new ArrayList<>()
                : allEmployees.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("data", pageData);
        response.put("currentPage", page);
        response.put("totalItems", totalItems);
        response.put("totalPages", (int) Math.ceil((double) totalItems / size));

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

