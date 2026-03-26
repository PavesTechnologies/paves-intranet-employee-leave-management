package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.service.LeaveBlockScheduler;
import com.paves.employee_leave_management.serviceInterface.ApprovalServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author paves
 */
@CrossOrigin
@RestController
@RequestMapping("/api/leave-balance")
@RequiredArgsConstructor
public class LeaveBalanceController {

    @Autowired
    private LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    private ApprovalServiceInterface approvalService;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private SimpMessagingTemplate template;

    @Autowired
    private LeaveRequestRepo leaveRequestRepo;

//    @Autowired
//    LeaveBalanceDAO leaveBalanceDao;

    // This is a placeholder for getting the user from the JWT token
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
//            String email = jwt.getClaim("email");  // "employee1@example.com"
            Long userId = jwt.getClaim("user_id"); // If needed

            return employeeRepo.findByEmployeeId(String.valueOf(userId))
                    .orElseThrow(() -> new RuntimeException("Employee not found for id: " + userId));
        }

        throw new RuntimeException("Invalid authentication principal");
    }


    @PostMapping("/generate/{employeeId}")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> generateLeaveBalance(@PathVariable String employeeId) {
        //employeeRepo.findAll().forEach(employee -> leaveBalanceService.createLeaveBalanceForNewEmployee(employee.getEmployeeId()));
        leaveBalanceService.createLeaveBalanceForNewEmployee(employeeId);

        return ResponseEntity.ok("Leave balance generated successfully for employee: " + employeeId);
    }

    @PostMapping("/carryforward")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> carryForward() {
        leaveBalanceService.processAccrualForLeaveType();
        return ResponseEntity.ok("Carry forward process completed.");
    }

    @GetMapping("/{balanceID}")
    @PreAuthorize("hasAnyRole('MANAGER','HR','GENERAL')")
    public ResponseEntity<LeaveBalance> getLeaveBalancesByBalanceId(@PathVariable String balanceID) {
        LeaveBalance leaveBalance =  leaveBalanceService.findByBalanceId(balanceID);
        return ResponseEntity.ok(leaveBalance);
    }

//    @GetMapping("/all-leave-balances")
//    @PreAuthorize("hasAnyRole('HR')")
//    public ResponseEntity<List<LeaveBalance>> getAllLeaveBalances() {
//        return leaveBalanceService.getAllLeaveBalances();
//    }

    @GetMapping("/all-leave-balances/{year}")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<AllPeopleLeaveBalance>> getAllLeaveBalance(@PathVariable Integer year) {
        List<AllPeopleLeaveBalance> leaveBalances =  leaveBalanceService.getAllLeaveBalanceByYear(year);
        return ResponseEntity.ok(leaveBalances);
    }

    @Cacheable("test")
    public String testCache() {
        System.out.println("🔥 DB HIT");
        return "Hello Redis";
    }

    @GetMapping("/leave-balance")
    public ResponseEntity<Map<String, Object>> getAllLeaveBalanceByYear(
            @RequestParam Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

//        testCache();
        return leaveBalanceService.getAllLeaveBalanceByYear(year, page, size);
    }


    @PostMapping("/upload-accruals")
    public ResponseEntity<UploadResponse> uploadAccruals(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username) {
        try {
            UploadResponse response = leaveBalanceService.handleAccruedUpload(file, username);
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

    @GetMapping("/download-template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        String filename = "Leave_Balance_Upload_Template.xlsx";

        // Get the excel file as a byte array from the service
        byte[] excelContent = leaveBalanceService.generateTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelContent);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    // STEP 1: Just read the excel and send data to UI for preview
    @PostMapping("/parse-excel")
    public ResponseEntity<List<LeaveBalanceDTO>> parseExcel(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(leaveBalanceService.parseExcel(file));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }



    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByEmployeeId(@PathVariable String employeeId) {
        template.convertAndSend("/topic/data-updated", "updated");
        return leaveBalanceService.findByEmployeeId(employeeId);
    }
    
    @GetMapping("/employee/{employeeId}/{year}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasAnyRole('HR','MANAGER','GENERAL')")
    public ApiResponse<EmployeeLeaveBalance> getLeaveBalancesByEmployeeIdAndYear(
            @PathVariable String employeeId, 
            @PathVariable Integer year) {
        template.convertAndSend("/topic/data-updated", "updated");
        EmployeeLeaveBalance balance = leaveBalanceService.findByEmployeeIdAndYearPerEmployee(employeeId, year);
        return new ApiResponse<>(true, "leave balance for "+employeeId+" "+year+" ", balance);
    }

    @GetMapping("/employee/drop/{employeeId}/{year}")
    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasAnyRole('HR','MANAGER','GENERAL')")
    public ApiResponse<EmployeeLeaveBalanceForDropdown> getLeaveBalancesByEmployeeIdAndYearForDropDown(
            @PathVariable String employeeId,
            @PathVariable Integer year) {
        template.convertAndSend("/topic/data-updated", "updated");
        EmployeeLeaveBalanceForDropdown balance = leaveBalanceService.getLeaveBalanceForDropdown(employeeId, year);
        return new ApiResponse<>(true, "leave balance for "+employeeId+" "+year+" ", balance);
    }

//    @GetMapping("/employee/{employeeId}/{year}")
//    @PreAuthorize("@permissionService.isOwner(authentication, #employeeId) or @permissionService.isManager(authentication, #employeeId) or hasRole('HR')")
//    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByEmployeeId(@PathVariable String employeeId,@PathVariable int year) {
//        template.convertAndSend("/topic/data-updated", "updated");
//        return leaveBalanceService.findByEmployeeIdAndYear(employeeId,year);
//    }


//    @PutMapping("/update-leave-balance-employee")
//    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
//        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
//    }

    @GetMapping("/type/{leaveTypeId}")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByLeaveName(@PathVariable String leaveTypeId) {
        return leaveBalanceService.findByLeaveId(leaveTypeId);
    }

    @PutMapping("/update-leave-balance-employee")
    @PreAuthorize("@permissionService.isOwner(authentication, #leaveBalance[0].employee.employeeId)")
    public ResponseEntity<List<LeaveBalance>> UpdateLeaveBalancesByEmployeeId(@RequestBody List<LeaveBalance> leaveBalance) {
        return leaveBalanceService.UpdateLeaveBalancesByEmployeeId(leaveBalance);
    }

    @PostMapping("/update-leave-balance")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> approveLeave(
            @RequestParam String employeeId,
            @RequestParam String leaveTypeId,
            @RequestParam double approvedDays
    ) {
        int currentYear = Year.now().getValue();
        leaveBalanceService.updateLeaveBalanceAfterApproval(employeeId, leaveTypeId, approvedDays, currentYear);
        return ResponseEntity.ok("Leave approved and balance updated successfully.");
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<ApiResponse<Object>> updateLeave(@RequestBody LeaveBalanceUpdateRequest request) {
        Employee maker = getAuthenticatedUser();
        String makerRole = "HR"; // or maker.getJobTitle()

        // ✅ Fetch current year balances (before state)
        List<LeaveBalance> beforeBalances =
                leaveBalanceService.getCurrentYearBalances(request.getEmployeeId());

        if (beforeBalances == null || beforeBalances.isEmpty()) {
            throw new LeaveBalanceExceptionHandler(
                    "No leave balances found for current year for employee: " + request.getEmployeeId());
        }

        // ✅ Shape it exactly as required
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("employeeId", request.getEmployeeId());
        oldData.put("balances", beforeBalances);
//        oldData.put("performedBy", null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("oldData", oldData);
        payload.put("newData", request);

        // ✅ Build Approval DTO
        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.UPDATE_EMPLOYEE_LEAVE_BALANCE);
        dto.setPayload(payload);

        // ✅ Submit for approval
        approvalService.submitForApproval(dto, maker, makerRole);

        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Request to update leave balances has been submitted for approval.",
                null
        ));
    }

    @GetMapping("/search/{year}")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<LeaveBalance>> search(@RequestParam(value = "query", required = false) String query, @PathVariable int year) {
        List<LeaveBalance> results = leaveBalanceService.searchLeaveBalances(query, year);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/autocomplete")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<List<String>> autocomplete(@RequestParam("query") String query) {
        List<String> suggestions = leaveBalanceService.autocompleteEmployee(query);
        return ResponseEntity.ok(suggestions);
    }

    @Autowired
    LeaveBlockScheduler lbs;
    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<String> monthly() {
//        leaveBalanceService.processAccrualForLeaveType();
        lbs.deactivateDueLeaveTypes();
        return ResponseEntity.ok("Monthly process triggered successfully.");
    }


    @PostMapping("/process-carry-forwards/{year}")
    @PreAuthorize("hasAnyRole('HR')")
    public ApiResponse<Object> processCurryForwards(@PathVariable int year){

        List<LeaveRequest> pendingLeaveRequests = leaveRequestRepo.findByStatus(LeaveStatus.PENDING);
        if (!pendingLeaveRequests.isEmpty()){
            return new ApiResponse<>(false, "There are pending requests to process", pendingLeaveRequests);
        }
        Employee maker = getAuthenticatedUser();
        String role = "HR";

        Map<String, Object>  payload = new HashMap<>();
        payload.put("year", year);

        MCApprovalRequestDto dto = new MCApprovalRequestDto();
        dto.setActionType(ActionType.YEAR_LEAVE_PROCESSING);
        dto.setPayload(payload);

        approvalService.submitForApproval(dto, maker, role);

        return new ApiResponse<>(true, "send request to HR Manager", null );
    }


}
