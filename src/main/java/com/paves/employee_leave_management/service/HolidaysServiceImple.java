package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.HolidayNameDateDto;
import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.enums.HolidayType;
import com.paves.employee_leave_management.globalExceptionHandler.HolidayExceptionHandler;
import com.paves.employee_leave_management.repo.HolidayRepo;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import jakarta.persistence.Id;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class HolidaysServiceImple implements HolidaysServiceInterface {

    @Autowired
    HolidayRepo holidayRepo;

    @Autowired
    private DataSource dataSource;

    @Override
    public ResponseEntity<List<Holidays>> getAllHolidays() {

        List<Holidays> holidays = holidayRepo.findAll();
        if (holidays.isEmpty())
            throw new HolidayExceptionHandler("No holidays found");
        return ResponseEntity.ok(holidays);
    }

    @Override
    public ResponseEntity<Holidays> getHolidayById(Long id) {
        Holidays holiday = holidayRepo.findById(id)
                .orElseThrow(() -> new HolidayExceptionHandler("No holiday found for this id"));

        return ResponseEntity.ok(holiday);

    }

    @Override
    public ResponseEntity<String> addHoliday(List<Holidays> holidays) {
        for (Holidays holiday : holidays) {
            boolean exists = holidayRepo.existsByHolidayDateAndStateAndYear(
                    holiday.getHolidayDate(),
                    holiday.getState(),
                    holiday.getYear()
            );

            if (exists) {
                throw new HolidayExceptionHandler("Holiday already exists for date: "
                        + holiday.getHolidayDate() + ", state: " + holiday.getState()
                        + ", year: " + holiday.getYear());
            }
        }

        holidayRepo.saveAll(holidays);
        return ResponseEntity.ok("Holidays added successfully");
    }


    @Override
    public ResponseEntity<String> updateHoliday(Holidays holidays) {
        Holidays existingHoliday = holidayRepo.findById(holidays.getHolidayId())
                .orElseThrow(() -> new HolidayExceptionHandler("No holiday found for this id"));
        holidayRepo.save(holidays);
        return ResponseEntity.ok("Holiday updated successfully");
    }

    @Override
    public ResponseEntity<String> deleteHoliday(Long id) {
        holidayRepo.findById(id).orElseThrow(() -> new HolidayExceptionHandler("No holiday found for this id"));
        holidayRepo.deleteById(id);
        return ResponseEntity.ok("Holiday deleted successfully");
    }

    @Override
    public ResponseEntity<List<Holidays>> getHolidaysByYear(int year) {
        LocalDate today = LocalDate.now();

        List<Holidays> holidays = holidayRepo.findByYear(year)
                .orElseThrow(() -> new HolidayExceptionHandler("No holidays found for this year"))
                .stream()
                .peek(h -> h.setIsActive(!h.getHolidayDate().isBefore(today)))
                .sorted(
                        Comparator.comparing(Holidays::getIsActive).reversed() // active first
                                .thenComparing(Holidays::getHolidayDate)       // then by date
                )
                .collect(Collectors.toList());

        holidayRepo.saveAll(holidays);
        return ResponseEntity.ok(holidays);
    }


    @Override
    public ResponseEntity<String> deleteHolidaysByYear(int year) {
        holidayRepo.findByYear(year).orElseThrow(() -> new HolidayExceptionHandler("No holidays found for this year"));
        holidayRepo.deleteByYear(year);
        return ResponseEntity.ok("Holidays deleted successfully for year: " + year);
    }

    @Override
    public ResponseEntity<String> createHolidaysForCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        int lastYear = currentYear - 1;

        List<Holidays> lastYearHolidays = getHolidaysByYear(lastYear).getBody();

        List<Holidays> newYearHolidays = lastYearHolidays.stream()
                .map(h -> {
                    Holidays newHoliday = new Holidays();
                    newHoliday.setHolidayName(h.getHolidayName());
                    newHoliday.setHolidayDate(h.getHolidayDate().withYear(currentYear));
                    newHoliday.setHolidayDescription(h.getHolidayDescription());
                    newHoliday.setType(h.getType());
                    newHoliday.setState(h.getState());
                    return newHoliday;
                })
                .toList();

        holidayRepo.saveAll(newYearHolidays);

        return ResponseEntity.ok("Holidays created successfully for year: " + currentYear);
    }

    @Override
    public ResponseEntity<String> deleteHolidaysThreeYearsAgo() {
        int threeYearsAgo = LocalDate.now().getYear() - 3;
        deleteHolidaysByYear(threeYearsAgo);
        return ResponseEntity.ok("Holidays deleted successfully for year: " + threeYearsAgo);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    if (val == Math.floor(val)) {
                        return String.valueOf((long) val);
                    } else {
                        return String.valueOf(val);
                    }
                }

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }

            case BLANK:
            default:
                return null;
        }
    }

    private LocalDate parseDateCell(Cell cell) {
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            // Excel true date cell
            return cell.getLocalDateTimeCellValue().toLocalDate();
        } else {
            // Try parsing as text
            String text = getCellValueAsString(cell);
            if (text == null || text.isEmpty()) return null;
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }

    @Override
    public void importHolidaysFromExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header row
                Row row = sheet.getRow(i);
                if (row == null) continue;

                long holidayId = Long.parseLong(getCellValueAsString(row.getCell(0)));
                String holidayName = getCellValueAsString(row.getCell(1));
                LocalDate holidayDate = parseDateCell(row.getCell(2));
                String description = getCellValueAsString(row.getCell(3));

                String typeStr = getCellValueAsString(row.getCell(4));
                HolidayType type = HolidayType.valueOf(typeStr.toUpperCase());

                String state = getCellValueAsString(row.getCell(5));
                String country = getCellValueAsString(row.getCell(6));
                int year = Integer.parseInt(getCellValueAsString(row.getCell(7)));

                boolean exists = holidayRepo.existsByHolidayDateAndStateAndYear(holidayDate, state, year);
                if (!exists) {
                    Holidays holiday = new Holidays();
                    holiday.setHolidayId(holidayId);
                    holiday.setHolidayName(holidayName);
                    holiday.setHolidayDate(holidayDate);
                    holiday.setHolidayDescription(description);
                    holiday.setType(type);
                    holiday.setState(state);
                    holiday.setCountry(country);
                    holiday.setYear(year);

                    holidayRepo.save(holiday);
                }
            }
        }
    }

    public ByteArrayInputStream createHolidayTemplate() throws IOException, SQLException {
        List<String> headers = getTableHeaders();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Holidays Template");

            // --- Header font and style ---
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);

            // --- Header row ---
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerCellStyle);
            }

            // --- Auto-size columns ---
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private List<String> getTableHeaders() throws SQLException {
        List<String> headers = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Columns to skip (audit/system fields)
        Set<String> excludeColumns = Set.of(
                "id", "created_by", "created_at", "updated_by", "updated_at", "deleted_at", "is_active", "last_updated_at"
        );

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM holidays LIMIT 1");
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i).toLowerCase();


                if (!excludeColumns.contains(columnName) && seen.add(columnName)) {
                    headers.add(columnName);
                }
            }
        }

        return headers;
    }


    private String getPrimaryKeyColumnName(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return "holiday_id"; // Assuming the column name for holidayId is holiday_id
            }
        }
        return null; // Should not happen for a valid entity
    }

    @Override
    public ResponseEntity<List<HolidayNameDateDto>> getHolidaysByStateAndCountry(String state, String country) {
        int year = LocalDate.now().getYear();
        List<Holidays> holidays = holidayRepo.findByStateAndCountryAndYear(state, country, year);

        if (holidays.isEmpty()) {
            throw new HolidayExceptionHandler("No holidays found for state: " + state + " and country: " + country);
        }

        List<HolidayNameDateDto> result = holidays.stream()
                .map(h -> new HolidayNameDateDto(h.getHolidayName(), h.getHolidayDate()))
                .toList();

        return ResponseEntity.ok(result);
    }

    @Override
    public List<Holidays> getHolidaysByMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        return holidayRepo.findByMonth(month);
    }


}
