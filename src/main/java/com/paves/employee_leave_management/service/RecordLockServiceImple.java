package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.RecordLock;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.RecordLockRepository;
import com.paves.employee_leave_management.serviceInterface.RecordLockServiceInterface;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecordLockServiceImple implements RecordLockServiceInterface {

    private final RecordLockRepository lockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final LeaveRequestRepo leaveRequestRepo;

    // primary key lookup, lower-cased table name -> pk column
    private final Map<String, String> tablePkMap = new HashMap<>();
    private static final long LOCK_EXPIRY_MINUTES = 10;

    // Only these tables can be locked (prevents accidental locking of reference tables)
    private static final Set<String> LOCKABLE_TABLES = Set.of("leave_request", "leave_balance");

    @PostConstruct
    public void init() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                ResultSet pkRs = connection.getMetaData().getPrimaryKeys(null, null, tableName);
                if (pkRs.next()) tablePkMap.put(tableName.toLowerCase(), pkRs.getString("COLUMN_NAME"));
            }
        }
    }

    /**
     * Try to acquire a lock for the given record.
     * Cluster-safe because DB constraint is used and duplicate-key is handled.
     */
    @Override
    @Transactional
    public String lockRecord(String tableName, String recordId, String lockedBy) {
        String tn = tableName.toLowerCase(Locale.ROOT);

        if (!LOCKABLE_TABLES.contains(tn)) {
            return "Locking not supported for table: " + tableName;
        }

        // 1. Check record exists
        Map<String, Object> record = getRecord(tableName, recordId);
        if (record == null) return "Record does not exist";

        // 2. Dependency: leave_request → leave_balance
        if ("leave_request".equalsIgnoreCase(tn)) {
            String employeeId = Objects.toString(record.get("employee_id"), null);
            String leaveTypeId = Objects.toString(record.get("leave_type_id"), null);
            int year = LocalDate.now().getYear();
            if (employeeId != null && leaveTypeId != null) {
                LeaveBalance balance = leaveBalanceRepo
                        .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
                if (balance != null && isLocked("leave_balance", balance.getBalanceId())) {
                    return "Cannot apply/edit leave. Leave balance is being edited by "
                            + getLockedBy("leave_balance", balance.getBalanceId());
                }
            }
        }

        // 3. Dependency: leave_balance → leave_request
        if ("leave_balance".equalsIgnoreCase(tn)) {
            String employeeId = Objects.toString(record.get("employee_id"), null);
            String leaveTypeId = Objects.toString(record.get("leave_type_id"), null);
            Integer year = record.get("year") instanceof Integer ? (Integer) record.get("year") : null;
            if (employeeId != null && leaveTypeId != null && year != null) {
                List<Map<String, Object>> requests = jdbcTemplate.queryForList(
                        "SELECT leave_id FROM leave_request WHERE employee_id = ? AND leave_type_id = ? AND year = ?",
                        employeeId, leaveTypeId, year
                );
                for (Map<String, Object> req : requests) {
                    String leaveId = Objects.toString(req.get("leave_id"), null);
                    if (leaveId != null && isLocked("leave_request", leaveId)) {
                        return "Cannot edit leave balance. Leave request " + leaveId
                                + " is being edited by " + getLockedBy("leave_request", leaveId);
                    }
                }
            }
        }

        // 4. If an existing lock is present and not expired, report locked-by
        Optional<RecordLock> existingLockOpt = lockRepository.findByTableNameAndRecordId(tableName, recordId);
        if (existingLockOpt.isPresent()) {
            RecordLock lock = existingLockOpt.get();
            if (!lock.isExpired()) {
                return "Record is currently being edited by " + lock.getLockedBy();
            }
            // expired lock: remove it so we can try to insert new one
            lockRepository.delete(lock);
        }

        // 5. Insert new lock. Rely on DB unique constraint to avoid races across nodes.
        RecordLock newLock = RecordLock.builder()
                .tableName(tableName)
                .recordId(recordId)
                .lockedBy(lockedBy)
                .employeeId(Objects.toString(record.get("employee_id"), null))
                .lockedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(LOCK_EXPIRY_MINUTES))
                .build();

        try {
            lockRepository.saveAndFlush(newLock);
        } catch (DataIntegrityViolationException ex) {
            // someone else inserted a lock concurrently (duplicate unique constraint violation)
            Optional<RecordLock> lockNow = lockRepository.findByTableNameAndRecordId(tableName, recordId);
            if (lockNow.isPresent() && !lockNow.get().isExpired()) {
                return "Record is currently being edited by " + lockNow.get().getLockedBy();
            } else {
                // Unexpected: either expired record or transient. Try one more time (best-effort).
                try {
                    lockRepository.saveAndFlush(newLock);
                } catch (DataIntegrityViolationException ex2) {
                    return "Record is currently being edited by another user.";
                }
            }
        }

        return "Lock acquired successfully";
    }

    /**
     * Release lock if caller owns it or if lock expired.
     */
    @Override
    @Transactional
    public void releaseLock(String tableName, String recordId, String lockedBy) {
        lockRepository.findByTableNameAndRecordId(tableName, recordId)
                .ifPresent(lock -> {
                    if (lock.isExpired() || lock.getLockedBy().equals(lockedBy)) {
                        lockRepository.delete(lock);
                    }
                });
    }

    @Override
    public boolean isLocked(String tableName, String recordId) {
        return lockRepository.findByTableNameAndRecordId(tableName, recordId)
                .map(lock -> !lock.isExpired())
                .orElse(false);
    }

    @Override
    public String getLockedBy(String tableName, String recordId) {
        return lockRepository.findByTableNameAndRecordId(tableName, recordId)
                .map(RecordLock::getLockedBy)
                .orElse(null);
    }

    /**
     * Refresh the expiry for the lock if the caller is the owner.
     * The frontend should call this periodically (e.g. every 5 minutes while editing).
     */
    @Transactional
    public boolean refreshLock(String tableName, String recordId, String lockedBy) {
        Optional<RecordLock> opt = lockRepository.findByTableNameAndRecordId(tableName, recordId);
        if (opt.isEmpty()) return false;
        RecordLock lock = opt.get();
        if (!lock.getLockedBy().equals(lockedBy)) return false;
        lock.setExpiresAt(LocalDateTime.now().plusMinutes(LOCK_EXPIRY_MINUTES));
        lockRepository.save(lock);
        return true;
    }

    private Map<String, Object> getRecord(String tableName, String recordId) {
        String pkColumn = tablePkMap.get(tableName.toLowerCase(Locale.ROOT));
        if (pkColumn == null)
            throw new RuntimeException("Unknown table: " + tableName);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                String.format("SELECT * FROM %s WHERE %s = ?", tableName, pkColumn), recordId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Scheduled cleanup of expired locks.
     * Runs every minute. Uses a new transaction to avoid interfering with callers.
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupExpiredLocks() {
        lockRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
