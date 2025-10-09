package com.paves.employee_leave_management.controller;


import com.paves.employee_leave_management.serviceInterface.RecordLockServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/lock")
@CrossOrigin
@RequiredArgsConstructor
public class RecordLockController {
    private final RecordLockServiceInterface lockService;

    @PostMapping("/lock")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER')")
    public ResponseEntity<Map<String, Object>> lockRecord(@RequestBody Map<String, String> body) {
        String table = body.get("tableName");
        String recordId = body.get("recordId");
        String user = body.get("lockedBy");

        String result = lockService.lockRecord(table, recordId, user);
        boolean success = result.equals("Lock acquired successfully");

        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", result
        ));
    }

    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER')")
    public ResponseEntity<String> releaseLock(@RequestBody Map<String, String> body) {
        lockService.releaseLock(body.get("tableName"), body.get("recordId"), body.get("lockedBy"));
        return ResponseEntity.ok("Lock released");
    }

    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER')")
    public ResponseEntity<Map<String, Object>> checkLock(
            @RequestParam String tableName,
            @RequestParam String recordId) {

        boolean locked = lockService.isLocked(tableName, recordId);
        String lockedBy = lockService.getLockedBy(tableName, recordId);

        return ResponseEntity.ok(Map.of(
                "locked", locked,
                "lockedBy", lockedBy
        ));
    }
}
