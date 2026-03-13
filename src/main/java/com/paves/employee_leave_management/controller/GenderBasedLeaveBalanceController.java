package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.UploadResponse;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.ValidationAndExecution;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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

import java.io.IOException;

@RestController
@RequestMapping("/api/gender-base-leave-balance")
public class GenderBasedLeaveBalanceController {

    @Autowired
    private GenderBasedLeaveBalanceServiceInterface genderBasedLeaveBalanceService;

    @Autowired
    private ValidationAndExecution validationAndExecution;

    @Autowired
    private EmployeeRepo employeeRepo;

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

    @PostMapping("/update-leave-balance")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> updateLeaveBalanceForEmployee(
            @Valid @RequestBody GenderBasedLeaveBalance leaveBalance) {
        Employee maker = getAuthenticatedUser();
        String makerRole = "HR";

        ApiResponse<Object> response =
                validationAndExecution.updateValidateGenderBaseLeaveBalance(leaveBalance, maker, makerRole);

        return ResponseEntity
                .status(response.isSuccess() ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(response);
    }

    @GetMapping("/download-gender-template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        String filename = "Gender_Based_Leave_Balance_Upload_Template.xlsx";

        // Get the excel file as a byte array from the service
        byte[] excelContent = genderBasedLeaveBalanceService.generateTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelContent);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping("/upload-gender-accruals")
    public ResponseEntity<UploadResponse> uploadAccruals(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username) {
        try {
            UploadResponse response = genderBasedLeaveBalanceService.handleAccruedUpload(file, username);
            return ResponseEntity.ok(response);
        }catch (Exception e) {
            // We need to handle the specific case where the exception
            // might be a wrapper around our actual error list.
            return ResponseEntity.badRequest().body(UploadResponse.builder()
                    .message("Upload failed: " + e.getMessage())
                    .processedCount(0)
                    // .errors(???) <--- The error list is currently lost here
                    .build());
        }
    }

}
