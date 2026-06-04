package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.HolidayCheckResponse;
import com.paves.employee_leave_management.dto.HolidayNameDateDto;
import com.paves.employee_leave_management.dto.MCApprovalRequestDto;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.globalExceptionHandler.HolidayExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.HolidayRepo;
import com.paves.employee_leave_management.service.HolidaysServiceImple;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.paves.employee_leave_management.utils.UtilsMethods.getMakerRole;

@RestController
@RequestMapping("/api/holidays")
@CrossOrigin
public class HolidaysController {

    @Autowired
    private HolidayRepo holidayRepo;

    @Autowired
    private HolidaysServiceInterface holidaysService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private ApprovalServiceInterface approvalService;

    private Employee getAuthenticatedUser() {
        // In a real application, you would extract the user details from the Spring Security Context.
        // For now, we'll fetch a hardcoded user to simulate this.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            // You can fetch using email or user_id depending on your DB
            //String email = jwt.getClaim("email");  // "employee1@example.com"
            Long userId = jwt.getClaim("user_id"); // If needed

            return employeeRepo.findByEmployeeId(String.valueOf(userId))
                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
        }

        throw new RuntimeException("Invalid authentication principal");
    }

    // 🔹 Get all holidays
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR', 'GENERAL', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<Holidays>> getAllHolidays() {
        return holidaysService.getAllHolidays();
    }

    // 🔹 Get holiday by ID
    @GetMapping("/id/{id}")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<Holidays> getHolidayById(@PathVariable Long id) {
        return holidaysService.getHolidayById(id);
    }

    // 🔹 Add new holiday
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> addHoliday(@RequestBody List<Holidays> holidays, Authentication authentication) {

        String makerRole = getMakerRole(authentication);
        Employee maker = getAuthenticatedUser();


        if ("SUPER_ADMIN".equalsIgnoreCase(makerRole)) {
            try {
                ResponseEntity<String> result = holidaysService.addHoliday(holidays);
                return ResponseEntity.ok(new ApiResponse<>(true,
                        "Holidays added successfully by super admin.", null));
            } catch (HolidayExceptionHandler e) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponse<>(false, e.getMessage(), null));
            }
        }

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.ADD_HOLIDAY);




        Map<String, Object> payload = new HashMap<>();
        payload.put("newData", holidays);
        dto.setPayload(payload);



        approvalService.submitForApproval(dto, maker, makerRole);
//        return holidaysService.addHoliday(holidays);
        return ResponseEntity.ok(new ApiResponse<>(true, "Request to add holiday submitted successfully", null));
    }

    // 🔹 Update holiday
//    @PutMapping("/update")
//    @PreAuthorize("hasRole('HR')")
//    public ResponseEntity<String> updateHoliday(@RequestBody Holidays holidays) {// ensure correct holiday is updated
//
//        return holidaysService.updateHoliday(holidays);
//    }
    @PutMapping("/update") // Keep existing path and verb
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> submitUpdateHolidayRequest(
            @RequestBody Holidays updatedHolidayData // Body contains the full updated object
    ) { // Inject maker

        Long holidayId = updatedHolidayData.getHolidayId(); // Get ID from the body
        if (holidayId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Holiday ID is required for update.", null));
        }

//        log.info("Received request from {} to update holiday ID: {}", maker.getEmployeeId(), holidayId);
        Employee maker = null;
        try {
            // 1. Fetch the existing holiday for the "before" state
            Holidays existingHoliday = holidayRepo.findById(holidayId)
                    .orElseThrow(() -> new HolidayExceptionHandler("Cannot update: Holiday not found with ID " + holidayId));

            MCApprovalRequestDto dto = new MCApprovalRequestDto();
            dto.setActionType(ActionType.UPDATE_HOLIDAY);
            // 2. Create the payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("holidayId", holidayId);
            payload.put("beforeState", existingHoliday);
            payload.put("requestedState", updatedHolidayData);

            dto.setPayload(payload);

            maker = getAuthenticatedUser();

            approvalService.submitForApproval(dto, maker, "HR");

            // 3. Submit to the workflow engine
//            hrOperationService.submitUpdateHoliday(maker, payload); // Ensure method exists in HrOperationService

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to update holiday ID " + holidayId + " submitted for approval.",
                    null
            ));
        } catch (HolidayExceptionHandler e) {
//            log.warn("Holiday update failed for ID {}: {}", holidayId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
//            log.error("Error submitting holiday update request from user {} for ID {}: {}",
//                    maker.getEmployeeId(), holidayId, e.getMessage(),);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting holiday update request: " + e.getMessage(), null));
        }
    }

    // 🔹 Delete holiday
//    @DeleteMapping("/delete/{id}")
//    @PreAuthorize("hasRole('HR')")
//    public ResponseEntity<String> deleteHoliday(@PathVariable Long id) {
//
//        return holidaysService.deleteHoliday(id);
//    }
    @DeleteMapping("/delete/{id}") // Keep existing path
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> deleteHoliday(
            @PathVariable Long id) { // Inject maker

//        log.info("Received request from {} to delete holiday ID: {}", maker.getEmployeeId(), id);
        try {
            // 1. Verify exists
            if (!holidayRepo.existsById(id)) {
                throw new HolidayExceptionHandler("Cannot delete: Holiday not found with ID " + id);
            }
            Holidays holiday = holidayRepo.findById(id)
                    .orElseThrow(() -> new HolidayExceptionHandler("Cannot delete: Holiday not found with ID " + id));


            MCApprovalRequestDto dto = new MCApprovalRequestDto();
            dto.setActionType(ActionType.DELETE_HOLIDAY);

            // 2. Create payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("holidayId", holiday.getHolidayId());
            payload.put("holidayName", holiday.getHolidayName());
            payload.put("holidayDate", holiday.getHolidayDate());
            payload.put("description", holiday.getHolidayDescription());
            payload.put("Type", holiday.getType());
            payload.put("Country", holiday.getCountry());
            payload.put("year", holiday.getYear());
//            payload.put("active", holiday.);

            dto.setPayload(payload);

            // 3. Submit to workflow
            Employee maker = getAuthenticatedUser();
            approvalService.submitForApproval(dto, maker, "HR");


//            hrOperationService.submitDeleteHoliday(maker, payload); // Ensure method exists in HrOperationService

            return ResponseEntity.ok(new ApiResponse<>(
                    true,
                    "Request to delete holiday ID " + id + " submitted for approval.",
                    null
            ));
        } catch (HolidayExceptionHandler e) {
//            log.warn("Holiday deletion submission failed for ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
//            log.error("Error submitting holiday deletion request from user {} for ID {}: {}",
//                    maker.getEmployeeId(), id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error submitting holiday deletion request: " + e.getMessage(), null));
        }
    }

    @GetMapping("/year/{year}")
    @PreAuthorize("hasAnyRole('GENERAL', 'HR', 'SUPER_ADMIN', 'SYSTEM')")
    public ResponseEntity<List<Holidays>> getHolidaysByYear(@PathVariable int year) {
        List<Holidays> holidaysList =  holidaysService.getHolidaysByYear(year);
        return ResponseEntity.ok(holidaysList);
    }

    // 🔹 Delete all holidays for a specific year
    @DeleteMapping("/year/{year}")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ResponseEntity<String> deleteHolidaysByYear(@PathVariable int year) {
        return holidaysService.deleteHolidaysByYear(year);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
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
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN', 'ADMIN')")
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

        Optional<Holidays> holidayOpt = holidayRepo.findByHolidayDateAndYear(date, year);

        if (holidayOpt.isPresent()) {
            Holidays holiday = holidayOpt.get();
            return ResponseEntity.ok(
                    new HolidayCheckResponse("yes", holiday.getHolidayName(), date)
            );
        } else {
            return ResponseEntity.ok(
                    new HolidayCheckResponse("no", "Not a holiday", date)
            );
        }
    }

    @GetMapping("/by-location/{year}")
    @PreAuthorize("hasAnyRole('GENERAL', 'HR', 'MANAGER', 'SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<List<HolidayNameDateDto>> getHolidaysByStateAndCountry(
            @PathVariable int year,
            @RequestParam("state") String state,
            @RequestParam("country") String country) {
        return holidaysService.getHolidaysByStateAndCountry(state, country, year);
    }

    @GetMapping("/month/{month}")
    public ResponseEntity<List<Holidays>> getHolidaysByMonth(@PathVariable int month) {
        List<Holidays> holidays = holidaysService.getHolidaysByMonth(month);
        return ResponseEntity.ok(holidays);
    }


}

