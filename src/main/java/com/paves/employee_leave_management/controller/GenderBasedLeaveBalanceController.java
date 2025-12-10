package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.ValidationAndExecution;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
