package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.CdcFailureLog;
import com.paves.employee_leave_management.repo.CdcFailureLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cdc/failures")
@RequiredArgsConstructor
public class CdcFailureLogController {

    private final CdcFailureLogRepository cdcFailureLogRepository;

    // get all failed logs
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CdcFailureLog>> getFailures(
            @RequestParam(defaultValue = "FAILED") String status) {
        return ResponseEntity.ok(
                cdcFailureLogRepository.findByStatusOrderByCreatedAtDesc(
                        CdcFailureLog.CdcFailureStatus.valueOf(status)));
    }

    // get all exhausted (max retries hit)
    @GetMapping("/exhausted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CdcFailureLog>> getExhausted() {
        return ResponseEntity.ok(
                cdcFailureLogRepository.findByStatusOrderByCreatedAtDesc(
                        CdcFailureLog.CdcFailureStatus.EXHAUSTED));
    }
}