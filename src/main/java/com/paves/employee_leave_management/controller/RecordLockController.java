package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.serviceInterface.RecordLockServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/lock")
@CrossOrigin
@RequiredArgsConstructor
public class RecordLockController {

    private final RecordLockServiceInterface lockService;

    /**
     * Acquire a record lock.
     */
    @PostMapping("/lock")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER','HR-MANAGER')")
    public ResponseEntity<Map<String, Object>> lockRecord(@RequestBody Map<String, String> body) {
        String table = body.getOrDefault("tableName", "").trim();
        String recordId = body.getOrDefault("recordId", "").trim();
        String user = body.getOrDefault("lockedBy", "").trim();

        if (table.isEmpty() || recordId.isEmpty() || user.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Missing required parameters: tableName, recordId, or lockedBy"
            ));
        }

        String result = lockService.lockRecord(table, recordId, user);
        boolean success = result.equalsIgnoreCase("Lock acquired successfully");

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", result);
        response.put("table", table);
        response.put("recordId", recordId);

        return ResponseEntity.ok(response);
    }

    /**
     * Release a record lock.
     */
    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER','HR-MANAGER')")
    public ResponseEntity<Map<String, Object>> releaseLock(@RequestBody Map<String, String> body) {
        String table = body.getOrDefault("tableName", "").trim();
        String recordId = body.getOrDefault("recordId", "").trim();
        String user = body.getOrDefault("lockedBy", "").trim();

        if (table.isEmpty() || recordId.isEmpty() || user.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Missing required parameters: tableName, recordId, or lockedBy"
            ));
        }

        lockService.releaseLock(table, recordId, user);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lock released successfully",
                "table", table,
                "recordId", recordId
        ));
    }

    /**
     * Check lock status of a record.
     */
    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER','HR-MANAGER')")
    public ResponseEntity<Map<String, Object>> checkLock(
            @RequestParam String tableName,
            @RequestParam String recordId) {

        boolean locked = lockService.isLocked(tableName, recordId);
        String lockedBy = lockService.getLockedBy(tableName, recordId);

        return ResponseEntity.ok(Map.of(
                "locked", locked,
                "lockedBy", lockedBy,
                "table", tableName,
                "recordId", recordId
        ));
    }

    /**
     * Refresh an existing lock (extend expiry by configured duration).
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('HR','GENERAL','MANAGER','HR-MANAGER')")
    public ResponseEntity<Map<String, Object>> refreshLock(@RequestBody Map<String, String> body) {
        String table = body.getOrDefault("tableName", "").trim();
        String recordId = body.getOrDefault("recordId", "").trim();
        String user = body.getOrDefault("lockedBy", "").trim();

        if (table.isEmpty() || recordId.isEmpty() || user.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Missing required parameters: tableName, recordId, or lockedBy"
            ));
        }

        boolean refreshed = lockService.refreshLock(table, recordId, user);

        return ResponseEntity.ok(Map.of(
                "success", refreshed,
                "message", refreshed
                        ? "Lock refreshed successfully"
                        : "Lock not found or not owned by user",
                "table", table,
                "recordId", recordId
        ));
    }
}
