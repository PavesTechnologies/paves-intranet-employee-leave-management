package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.HolidayCheckResponse;
import com.paves.employee_leave_management.dto.HolidayNameDateDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.globalExceptionHandler.HolidayExceptionHandler;
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
import com.paves.employee_leave_management.security.CurrentUser; // Your annotation
import com.paves.employee_leave_management.service.HrOperationService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Add logging
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/holidays")
@CrossOrigin
public class HolidaysController {

    @Autowired
    private HolidayRepo holidayRepo;

    @Autowired
    private HolidaysServiceImple holidaysService;

    @Autowired
    private HrOperationService hrOperationService;

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
//    @PostMapping("/add")
//    @PreAuthorize("hasRole('HR')")
//    public ResponseEntity<String> addHoliday(@RequestBody List<Holidays> holidays) {
//        return holidaysService.addHoliday(holidays);
//    }
    @PostMapping("/add") // Changed path from "/submit-add"
    @PreAuthorize("hasRole('HR')") // Ensure correct role
    public ResponseEntity<ApiResponse<Object>> submitAddHolidayRequest(
            @RequestBody Holidays holidayData, // The data for the new holiday
            @CurrentUser Employee maker) { // Inject maker directly

        log.info("Received request from {} to add holiday: {}", maker.getEmployeeId(), holidayData.getHolidayName());
        try {
            // Call the HrOperationService to handle submission and start workflow
            hrOperationService.submitAddHoliday(maker, holidayData);

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to add holiday [" + holidayData.getHolidayName() + "] submitted for approval.",
                    null
            ));
        } catch (Exception e) {
            log.error("Error submitting add holiday request from user {}: {}", maker.getEmployeeId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting add holiday request: " + e.getMessage(), null));
        }
    }

    // 🔹 Update holiday
    /**
     * Submits a request to update an existing holiday, triggering the approval workflow.
     * Endpoint path: PUT /api/holidays/update
     */
    @PutMapping("/update") // Keep existing path and verb
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> submitUpdateHolidayRequest(
            @RequestBody Holidays updatedHolidayData, // Body contains the full updated object
            @CurrentUser Employee maker) { // Inject maker

        Long holidayId = updatedHolidayData.getHolidayId(); // Get ID from the body
        if (holidayId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Holiday ID is required for update.", null));
        }

        log.info("Received request from {} to update holiday ID: {}", maker.getEmployeeId(), holidayId);
        try {
            // 1. Fetch the existing holiday for the "before" state
            Holidays existingHoliday = holidayRepo.findById(holidayId)
                    .orElseThrow(() -> new HolidayExceptionHandler("Cannot update: Holiday not found with ID " + holidayId));

            // 2. Create the payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("holidayId", holidayId);
            payload.put("beforeState", existingHoliday);
            payload.put("requestedState", updatedHolidayData);

            // 3. Submit to the workflow engine
            hrOperationService.submitUpdateHoliday(maker, payload); // Ensure method exists in HrOperationService

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to update holiday ID " + holidayId + " submitted for approval.",
                    null
            ));
        } catch (HolidayExceptionHandler e) {
            log.warn("Holiday update failed for ID {}: {}", holidayId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error submitting holiday update request from user {} for ID {}: {}",
                    maker.getEmployeeId(), holidayId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting holiday update request: " + e.getMessage(), null));
        }
    }
//    @PutMapping("/update")
//    @PreAuthorize("hasRole('HR')")
//    public ResponseEntity<String> updateHoliday(@RequestBody Holidays holidays) {// ensure correct holiday is updated
//        return holidaysService.updateHoliday(holidays);
//    }

    // 🔹 Delete holiday
//    @DeleteMapping("/delete/{id}")
//    @PreAuthorize("hasRole('HR')")
//    public ResponseEntity<String> deleteHoliday(@PathVariable Long id) {
//        return holidaysService.deleteHoliday(id);
//    }

    /**
     * Submits a request to delete an existing holiday, triggering the approval workflow.
     * Endpoint path: DELETE /api/holidays/delete/{id}
     */
    @DeleteMapping("/delete/{id}") // Keep existing path
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> submitDeleteHolidayRequest(
            @PathVariable Long id, // Keep ID from path
            @CurrentUser Employee maker) { // Inject maker

        log.info("Received request from {} to delete holiday ID: {}", maker.getEmployeeId(), id);
        try {
            // 1. Verify exists
            if (!holidayRepo.existsById(id)) {
                throw new HolidayExceptionHandler("Cannot delete: Holiday not found with ID " + id);
            }

            // 2. Create payload
            Map<String, Object> payload = Map.of("holidayId", id);

            // 3. Submit to workflow
            hrOperationService.submitDeleteHoliday(maker, payload); // Ensure method exists in HrOperationService

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to delete holiday ID " + id + " submitted for approval.",
                    null
            ));
        } catch (HolidayExceptionHandler e) {
            log.warn("Holiday deletion submission failed for ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error submitting holiday deletion request from user {} for ID {}: {}",
                    maker.getEmployeeId(), id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting holiday deletion request: " + e.getMessage(), null));
        }
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

