package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.HolidayType;
import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.globalExceptionHandler.HolidayExceptionHandler;
import com.paves.employee_leave_management.repo.HolidayRepo;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
public class HolidaysServiceImple implements HolidaysServiceInterface {

    @Autowired
    HolidayRepo holidayRepo;
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

}
