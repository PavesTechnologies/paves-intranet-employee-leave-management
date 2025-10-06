package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.HolidayNameDateDto;
import com.paves.employee_leave_management.entities.HolidayType;
import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.globalExceptionHandler.HolidayExceptionHandler;
import java.io.ByteArrayInputStream;
import com.paves.employee_leave_management.repo.HolidayRepo;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@Service
public class HolidaysServiceImple implements HolidaysServiceInterface {

    @Autowired
    HolidayRepo holidayRepo;

    @Autowired
    private DataSource dataSource;
    @Override
    public ResponseEntity<List<Holidays>> getAllHolidays() {

        List<Holidays> holidays = holidayRepo.findAll();
        if(holidays.isEmpty())
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
        List<Holidays> holidays=holidayRepo.findByYear(year).orElseThrow(() -> new HolidayExceptionHandler("No holidays found for this year"));
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
        // Dynamically get headers from the database schema
        List<String> headers = getTableHeaders();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Holidays Template");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);

            // Create header cells from the dynamic list
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerCellStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * Connects to the database to retrieve the column names for the 'holidays' table.
     * It dynamically reads the table name from the @Table annotation on the Holidays entity
     * and excludes the primary key column.
     *
     * @return A list of column names.
     * @throws SQLException if a database access error occurs.
     */
    private List<String> getTableHeaders() throws SQLException {
        List<String> headers = new ArrayList<>();

        // Get table name from the @Table annotation of the entity
        Table tableAnnotation = Holidays.class.getAnnotation(Table.class);
        String tableName = (tableAnnotation != null) ? tableAnnotation.name() : "holidays";

        // Find the primary key column name to exclude it
        String primaryKeyColumn = getPrimaryKeyColumnName(Holidays.class);

        // Use try-with-resources for automatic connection closing
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // Get columns for the specified table
            try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    // Add column to headers list if it's not the primary key
                    if (!columnName.equalsIgnoreCase(primaryKeyColumn)) {
                        headers.add(columnName);
                    }
                }
            }
        }
        return headers;
    }

    /**
     * Finds the primary key column name of an entity using reflection.
     * @param entityClass The entity class.
     * @return The name of the primary key column.
     */
    private String getPrimaryKeyColumnName(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                // In JPA, if @Column is not present, the column name is derived from the field name.
                // This simple logic assumes snake_case, which is common.
                // For a more robust solution, you'd check for a @Column(name="...") annotation.
                return "holiday_id"; // Assuming the column name for holidayId is holiday_id
            }
        }
        return null; // Should not happen for a valid entity
    }

    @Override
    public ResponseEntity<List<HolidayNameDateDto>> getHolidaysByStateAndCountry(String state, String country) {
        int year = LocalDate.now().getYear();
        List<Holidays> holidays = holidayRepo.findByStateAndCountryAndYear(state, country,year);

        if (holidays.isEmpty()) {
            throw new HolidayExceptionHandler("No holidays found for state: " + state + " and country: " + country);
        }

        List<HolidayNameDateDto> result = holidays.stream()
                .map(h -> new HolidayNameDateDto(h.getHolidayName(), h.getHolidayDate()))
                .toList();

        return ResponseEntity.ok(result);
    }


}
