package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/compoff")
@RequiredArgsConstructor
public class LeaveCompoffController {

    @Autowired
    LeaveCompoffSerivceInterface compoffService;

    @PostMapping("/request")
    @PreAuthorize("hasRole('GENERAL')")
    public ResponseEntity<ApiResponse<String>> requestCompoff(@RequestBody LeaveCompoffRequestDTO dto) {
        try {
            compoffService.requestCompoff(dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Compoff requested successfully.", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error: " + e.getMessage(), null));
        }
    }

    @PutMapping("/approve")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<String>> approveCompoff(@RequestBody ApproveRejectCompoffDTO dto) {
        try {
            compoffService.approveCompoff(dto.getCompoffId());
            return ResponseEntity.ok(new ApiResponse<>(true, "Compoff approved successfully.", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Approval failed: " + e.getMessage(), null));
        }
    }

    @PutMapping("/reject")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public ResponseEntity<ApiResponse<String>> rejectCompoff(@RequestBody ApproveRejectCompoffDTO dto) {
        try {
            compoffService.rejectCompoff(dto.getCompoffId());
            return ResponseEntity.ok(new ApiResponse<>(true, "Compoff rejected successfully.", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Rejection failed: " + e.getMessage(), null));
        }
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER','GENERAL', 'HR')")
    public ResponseEntity<ApiResponse<List<LeaveCompoff>>> getByEmployee(@PathVariable String employeeId) {
        List<LeaveCompoff> compoffs = compoffService.getCompoffsByEmployee(employeeId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Compoff list fetched", compoffs));
    }

    @PostMapping("/manager/status")
    @PreAuthorize("hasAnyRole('GENERAL')")
    public ResponseEntity<ApiResponse<List<LeaveCompoff>>> getByManagerAndStatus(@RequestBody ManagerCompoffStatusDTO dto) {
        List<LeaveCompoff> compoffs = compoffService.getCompoffsByManagerAndStatus(dto.getManagerId(), dto.getStatus());
        return ResponseEntity.ok(new ApiResponse<>(true, "Filtered Compoff list", compoffs));
    }

    @PostMapping("/pending")
    @PreAuthorize("hasAnyRole('MANAGER','GENERAL')")
    public ResponseEntity<ApiResponse<List<PendingCompoffResponseDTO>>> getPendingCompoffs(
            @RequestBody ManagerPendingCompoffDTO managerDTO) {

        String managerId = managerDTO.getManagerId();
        List<PendingCompoffResponseDTO> pendingCompoffs = compoffService.getPendingCompoffsForManager(managerId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Pending Compoffs fetched", pendingCompoffs));
    }

    @PutMapping("/employee/cancle")
    @PreAuthorize("hasAnyRole('GENERAL')")
    public ResponseEntity<ApiResponse<String>> cancelPendingCompOffByEmployee(@RequestBody Long id){
        try{
            compoffService.cancelPendingCompOffByEmployee(id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Pending CompOff request cancelled", null));
        }    catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Cancellation failed: " + e.getMessage(), null));
        }
    }

}
