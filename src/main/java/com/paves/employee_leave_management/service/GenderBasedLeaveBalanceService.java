package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.RowError;
import com.paves.employee_leave_management.dto.UploadResponse;
import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.GenderBasedLeaveBalancesRepo;
import com.paves.employee_leave_management.repo.GenderBasedRepo;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Override
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
                    "Remaining days", // Index 2
                    "Used days",   // Index 3
                    "Total Entitled Days",  // Index 4
                    "Year"           // Index 5
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
            sampleRow.createCell(1).setCellValue("L-PL/L-ML");        // Leave Type ID
            sampleRow.createCell(2).setCellValue(5);           // remaining days
            sampleRow.createCell(3).setCellValue(0);           // used days
            sampleRow.createCell(4).setCellValue(5);           // Total Entitled days
            sampleRow.createCell(5).setCellValue(2026);          // year

            // 4. Auto-size columns after adding data
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
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
                    int  remainingDays = (int)row.getCell(2).getNumericCellValue();
                    int usedDays = (int)row.getCell(3).getNumericCellValue();
                    int  totalEntitledDays= (int)row.getCell(4).getNumericCellValue();
                    int  year = (int)row.getCell(5).getNumericCellValue();

                    // 2. Validate Employee & LeaveType
                    Employee employee = employeeRepo.findById(empId)
                            .orElseThrow(() -> new RuntimeException("Employee not found: " + empId));

                    GenderBasedLeave leaveType = genderBasedRepo.findById(typeId)
                            .orElseThrow(() -> new RuntimeException("Leave Type not found: " + typeId));

                    // 3. Find existing or create new
                    // 1. Try to find existing record
                    Optional<GenderBasedLeaveBalance> existingBalance = leaveBalanceRepo
                            .findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(empId, typeId, year); // ✅ fixed

                    GenderBasedLeaveBalance balance;
                    if (existingBalance.isPresent()) {
                        balance = existingBalance.get(); // has balanceId → JPA will UPDATE
                    } else {
                        balance = new GenderBasedLeaveBalance();
                        balance.setCreatedAt(LocalDateTime.now()); // only on new records
                    }

                    balance.setEmployeeId(employee.getEmployeeId());
                    balance.setLeaveType(leaveType);
                    balance.setUpdatedAt(LocalDateTime.now());
                    balance.setUsedDays(usedDays);
                    balance.setTotalEntitledDays(totalEntitledDays);
                    balance.setRemainingDays(remainingDays);
                    balance.setYear(year);

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


    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }
}
