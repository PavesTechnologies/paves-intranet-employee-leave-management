package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.HolidayCheckResponse;
import com.paves.employee_leave_management.dto.HolidayNameDateDto;
import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.repo.HolidayRepo;
import com.paves.employee_leave_management.service.HolidaysServiceImple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/holidays")
@CrossOrigin
public class HolidaysController {

    @Autowired
    private HolidayRepo holidayRepo;

    @Autowired
    private HolidaysServiceImple holidaysService;

    // 🔹 Get all holidays
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER')")
    public ResponseEntity<List<Holidays>> getAllHolidays() {
        return holidaysService.getAllHolidays();
    }

    // 🔹 Get holiday by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Holidays> getHolidayById(@PathVariable Long id) {
        return holidaysService.getHolidayById(id);
    }

    // 🔹 Add new holiday
    @PostMapping("/add")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> addHoliday(@RequestBody List<Holidays> holidays) {
        return holidaysService.addHoliday(holidays);
    }

    // 🔹 Update holiday
    @PutMapping("/update")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> updateHoliday(@RequestBody Holidays holidays) {// ensure correct holiday is updated
        return holidaysService.updateHoliday(holidays);
    }

    // 🔹 Delete holiday
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteHoliday(@PathVariable Long id) {
        return holidaysService.deleteHoliday(id);
    }

    @GetMapping("/year/{year}")
    @PreAuthorize("hasAnyRole('HR', 'GENERAL') ")
    public ResponseEntity<List<Holidays>> getHolidaysByYear(@PathVariable int year) {
        return holidaysService.getHolidaysByYear(year);
    }

    // 🔹 Delete all holidays for a specific year
    @DeleteMapping("/year/{year}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteHolidaysByYear(@PathVariable int year) {
        return holidaysService.deleteHolidaysByYear(year);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> uploadExcel(@RequestParam("file") MultipartFile file) {
        try {
            holidaysService.importHolidaysFromExcel(file);
            return ResponseEntity.ok("Holidays imported successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body("Failed to import: " + e.getMessage());
        }
    }

    @GetMapping("/template/download")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException, SQLException {
        String filename = "holidays_template.xlsx";
        ByteArrayInputStream inputStream = holidaysService.createHolidayTemplate();

        HttpHeaders headers = new HttpHeaders();
        // This header tells the browser to download the file with the given filename
        headers.add("Content-Disposition", "attachment; filename=" + filename);

        return ResponseEntity
                .ok()
                .headers(headers)
                // This content type is for modern .xlsx Excel files
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/check")
//    @PreAuthorize("hasRole('GENERAL')")
    public ResponseEntity<?> checkHoliday(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        int year = date.getYear();

        Optional<Holidays> holidayOpt = holidayRepo.findByHolidayDateAndYear(date,year);

        if (holidayOpt.isPresent()) {
            Holidays holiday = holidayOpt.get();
            return ResponseEntity.ok(
                    new HolidayCheckResponse("yes", holiday.getHolidayName(),date)
            );
        } else {
            return ResponseEntity.ok(
                    new HolidayCheckResponse("no", "Not a holiday",date)
            );
        }
    }
    @GetMapping("/by-location")
    @PreAuthorize("hasAnyRole('GENERAL','HR','MANAGER')")
    public ResponseEntity<List<HolidayNameDateDto>> getHolidaysByStateAndCountry(
            @RequestParam("state") String state,
            @RequestParam("country") String country) {
        return holidaysService.getHolidaysByStateAndCountry(state, country);
    }

    @GetMapping("/month/{month}")
    public ResponseEntity<List<Holidays>> getHolidaysByMonth(@PathVariable int month) {
        List<Holidays> holidays = holidaysService.getHolidaysByMonth(month);
        return ResponseEntity.ok(holidays);
    }
}

