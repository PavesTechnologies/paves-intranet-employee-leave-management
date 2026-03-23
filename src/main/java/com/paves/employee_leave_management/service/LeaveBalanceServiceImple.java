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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        LocalDate today = LocalDate.now();

        // Skip all accruals on Jan 1st — carry-forward must be triggered manually first
        boolean isNewYearDay = (today.getMonthValue() == 1 && today.getDayOfMonth() == 1);
        if (isNewYearDay) {
            for(LeaveType type: types){
                if (!type.getActive()) continue;
                List<LeaveBalance> balances = leaveBalanceRepo
                        .findAllByYearAndLeaveTypeLeaveTypeId(today.getYear()-1, type.getLeaveTypeId());

                List<LeaveBalance> nextYearBalances = balances.stream()
                                .map(b -> {
                                    LeaveBalance nb = new LeaveBalance();
                                    // Existing fields
                                    nb.setEmployee(b.getEmployee());
                                    nb.setEmployeeId(b.getEmployee().getEmployeeId()); // Add this
                                    nb.setYear(b.getYear() + 1);
                                    nb.setAccruedLeaves(type.getAccrualRate());
                                    nb.setRemainingLeaves(type.getAccrualRate());
                                    nb.setLeaveType(type);
                                    nb.setEncashedLeaves(b.getEncashedLeaves()); // Fixed: was nb.getEncashedLeaves()
                                    nb.setBlockId(b.getBlockId()); // Fixed: was nb.getBlockId()
                                    nb.setIsBlocked(b.getIsBlocked()); // Fixed: was nb.getIsBlocked()
                                    nb.setCarriedForward(0);
                                    nb.setLastAccrualDate(LocalDate.now());
                                    nb.setLastUpdatedAt(null);
                                    nb.setUsedLeaves(b.getUsedLeaves());

                                    // Add missing fields
                                    nb.setTotalLeaves(type.getMaxDaysPerYear()); // Add this
                                    nb.setExpiredLeaves(0.0); // Initialize to 0
                                    nb.setCreateAt(LocalDateTime.now()); // Set creation timestamp

                                    return nb;
                                })
                                .collect(Collectors.toList());

                        leaveBalanceRepo.saveAll(nextYearBalances);
            }
            return;
        }

        for (LeaveType type : types) {
            if (!type.getActive()) continue;

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
                    if (today.getDayOfMonth() == 1 &&
                            (today.getMonthValue() == 4 ||
                                    today.getMonthValue() == 7 ||
                                    today.getMonthValue() == 10)) {
                        runMonthlyAccrual(type);
                    }
                    break;
                case YEARLY:
                    // No cron-based yearly accrual — handled via manual carry-forward
                    break;
                case NONE:
                    // No accrual — handled via manual carry-forward
                    break;
            }
        }
    }

    @Override
    public void runMonthlyAccrual(LeaveType type) {

        // Only process leave balances for THIS specific leave type this year
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() == 1 && today.getDayOfMonth() == 1) {
            runYearlyAccrual(type);// ← calls your exact yearly logic
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
    @Transactional
    public void runYearlyAccrual(LeaveType type) {

        List<LeaveBalance> balances =
                leaveBalanceRepo.findAllByYearAndLeaveTypeLeaveTypeId(
                        LocalDate.now().getYear() - 1, type.getLeaveTypeId()
                );

        if (balances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler("No Leave Balances found yearly");
        }

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

            if (isNewLeaveType) {
                // Requirement 1: For new leave types, ALWAYS start from today (referenceDate),
                // even if the effective date is in the past.
                // Exception: if the employee's hire date is in the future, use that instead.
                LocalDate startDate = hireDate.isAfter(referenceDate) ? hireDate : referenceDate;

                totalLeaves = lt.getAccrualRate() * calculateRemainingMonths(startDate);

                // Requirement 2: If today is before the 15th, credit the current month's accrual immediately.
                // Otherwise, accrued stays 0 and the first accrual happens on 1st of next month.
                accruedLeaves = (referenceDate.getDayOfMonth() < 15) ? lt.getAccrualRate() : 0;

            } else {
                // Existing employee with an already-active leave type
                LocalDate effectiveDate = lt.getEffectiveStartDate();
                LocalDate startDate = hireDate.isAfter(effectiveDate) ? hireDate : effectiveDate;
                startDate = startDate.isAfter(referenceDate) ? startDate : referenceDate;

                totalLeaves = lt.getAccrualRate() * calculateRemainingMonths(startDate);
                accruedLeaves = getAccruedLeaves(hireDate, referenceDate, lt.getAccrualRate(), lt.getEffectiveStartDate());

                if (lt.getLeaveName().equalsIgnoreCase(LeaveTypesEnum.EARNED_LEAVE.toString())) {
                    carriedForward = calculateEarnedLeaveCarryForward(hireDate, currentYear, lt);
                }
            }

        } else {
            // Fixed leave (e.g. Sick, Casual) — full quota available immediately
            totalLeaves = lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear() : 0;
            accruedLeaves = totalLeaves;
        }

        // If current month was already accrued (day < 15), lastAccrualDate = 1st of this month.
        // Otherwise, first accrual hasn't happened yet → set it to 1st of next month.
        LocalDate lastAccrualDate = (isNewLeaveType && referenceDate.getDayOfMonth() < 15)
                ? referenceDate.withDayOfMonth(1)
                : referenceDate.plusMonths(1).withDayOfMonth(1);

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
    public List<LeaveBalance> findByEmployeeIdAndYear(String employeeId, Integer year) {
         return leaveBalanceRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);
    }

    @Override
    public EmployeeLeaveBalance findByEmployeeIdAndYearPerEmployee(String employeeId, Integer year){
                List<LeaveBalance> regular = leaveBalanceRepo.findByEmployee_EmployeeIdAndYear(employeeId, year);
                List<GenderBasedLeaveBalance> genderBasedLeaveBalances = genderBasedLeaveBalancesRepo.findByEmployeeIdAndYear(employeeId, year);

                EmployeeLeaveBalance employeeLeaveBalance = new EmployeeLeaveBalance();
                employeeLeaveBalance.setGenderBasedLeaveBalances(genderBasedLeaveBalances);
                employeeLeaveBalance.setRegular(regular);
                return employeeLeaveBalance;
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

    @Transactional
    @Override
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
}

