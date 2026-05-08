package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.AddGenderBasedLeave;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveServiceInterface;
import com.paves.employee_leave_management.serviceInterface.ValidationAndExecution;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gender-base-leave")
public class GenderBaseLeaveController {

    @Autowired
    private GenderBasedLeaveServiceInterface genderBaseLeaveService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private ValidationAndExecution validationAndExecution;


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
            Long userId = jwt.getClaim("user_id");// If needed

            return employeeRepo.findByEmployeeId(String.valueOf(userId))
                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
        }

        throw new RuntimeException("Invalid authentication principal");
    }

    private String getMakerRole(Authentication authentication){
        return  authentication.getAuthorities().
                stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth->auth.replace("ROLE_", ""))
                .findFirst()
                .orElse("HR");
    }

    @PostMapping(
            value = "/add-leave",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> createGenderBaseLeave(
            @Valid @RequestBody GenderBasedLeave genderBasedLeave, Authentication authentication) {

        Employee maker = getAuthenticatedUser();
        String makerRole = getMakerRole(authentication);

        ApiResponse<Object> response =
                validationAndExecution.validateGenderBaseLeave(genderBasedLeave, maker, makerRole);

        return ResponseEntity
                .status(response.isSuccess() ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(response);
    }

    @GetMapping("/all-leave-types")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<Object> getAllLeaveTypes(){
        List<GenderBasedLeave> genderBasedLeaves = genderBaseLeaveService.getAllLeaveTypes();
        if(genderBasedLeaves.isEmpty()){
            return new ApiResponse<>(false,
                    "No active leave types found",
                    null);
        }

        return new ApiResponse<>(true,
                "Leave types fetched successfully",
                genderBasedLeaves);
    }
}
