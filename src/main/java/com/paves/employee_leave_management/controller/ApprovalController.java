//package com.paves.employee_leave_management.controller;
//
//import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.ApprovalRequestResponseDto;
//import com.paves.employee_leave_management.dto.ApproveRequestDto;
//import com.paves.employee_leave_management.dto.RejectRequestDto;
////import com.paves.employee_leave_management.entities.ApprovalRequest;
//import com.paves.employee_leave_management.entities.Employee;
//import com.paves.employee_leave_management.repo.EmployeeRepo;
//import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//@RestController
//@RequestMapping("/api/approvals")
//public class ApprovalController {
//
//    @Autowired
//    private EmailServiceInterface.ApprovalService approvalService;
//
//    @Autowired
//    private EmployeeRepo employeeRepo;
//
//    // This is a placeholder for getting the user from the JWT token
//    private Employee getAuthenticatedUser() {
//        // In a real application, you would extract the user details from the Spring Security Context.
//        // For now, we'll fetch a hardcoded user to simulate this.
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//
//        if (authentication == null || !authentication.isAuthenticated()) {
//            throw new RuntimeException("No authenticated user found");
//        }
//
//        Object principal = authentication.getPrincipal();
//
//        if (principal instanceof Jwt jwt) {
//            // You can fetch using email or user_id depending on your DB
////            String email = jwt.getClaim("email");  // "employee1@example.com"
//            Long userId = jwt.getClaim("user_id"); // If needed
//
//            return employeeRepo.findByEmployeeId(String.valueOf(userId))
//                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
//        }
//
//        throw new RuntimeException("Invalid authentication principal");
//    }
//
//    @PostMapping("/{requestId}/approve")
////    @PreAuthorize("hasRole('HR')") // Or a more specific checker role
//    public ResponseEntity<ApiResponse<Object>> approveRequest(@PathVariable Long requestId, @RequestBody ApproveRequestDto dto) {
//        Employee checker = getAuthenticatedUser();
//
//        approvalService.approveRequest(requestId, dto, checker);
//        return ResponseEntity.ok(new ApiResponse<>(true,"Request approved successfully.",null));
//    }
//
//    @PostMapping("/{requestId}/reject")
////    @PreAuthorize("hasRole('HR')") // Or a more specific checker role
//    public ResponseEntity<ApiResponse<Object>> rejectRequest(@PathVariable Long requestId, @RequestBody RejectRequestDto dto) {
//        Employee checker = getAuthenticatedUser();
//
//        approvalService.rejectRequest(requestId, dto, checker);
//        return ResponseEntity.ok(new ApiResponse<>(true,"Request rejected successfully.",null));
//    }
//
////
////    @GetMapping("/pending")
////    @PreAuthorize("hasAnyRole('HR', 'MANAGER', 'GENERAL', 'HR_ADMINISTRATOR')") // Roles that can be approvers
////    public ResponseEntity<List<ApprovalRequest>> getPendingRequests() {
////        Employee checker = getAuthenticatedUser();
//    /// /        System.out.println(checker);
//    /// /        System.out.println("eyuuuuuuuuuuu");
////        List<ApprovalRequest> pendingRequests = approvalService.getPendingApprovalsForUser(checker);
////        return ResponseEntity.ok(pendingRequests);
////    }
//    @GetMapping("/pending")
////    @PreAuthorize("hasAnyRole('HR', 'MANAGER', 'GENERAL', 'HR_ADMINISTRATOR')") // Roles that can be approvers
//    public ResponseEntity<ApiResponse<List<ApprovalRequestResponseDto>>> getPendingRequests() {
//        Employee checker = getAuthenticatedUser();
//        System.out.println(checker);
//        List<ApprovalRequestResponseDto> pendingRequests = approvalService.getPendingApprovalsForUser(checker);
//        System.out.println(pendingRequests);
//        return ResponseEntity.ok(new ApiResponse<>(true,"Pending Requests",pendingRequests));
//    }
//}
