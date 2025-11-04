package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.RecordLock;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.RecordLockRepository;
import com.paves.employee_leave_management.serviceInterface.RecordLockServiceInterface;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordLockServiceImple implements RecordLockServiceInterface {

    private final RecordLockRepository lockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final LeaveRequestRepo leaveRequestRepo;

    private final Map<String, String> tablePkMap = new HashMap<>();
    private static final long LOCK_EXPIRY_MINUTES = 10;

    // Load primary keys at startup
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
        System.out.println("Primary keys loaded: " + tablePkMap);
    }

    @Override
    public synchronized String lockRecord(String tableName, String recordId, String lockedBy) {
        // 1️⃣ Get the record from DB


        Map<String, Object> record = getRecord(tableName, recordId);
        if (record == null) return "Record does not exist";
//        if(lockRepository.existsByRecordIdAndEmployeeId(recordId,(String) record.get("employee_id"))){
//            return "";
//        }

        // 2️⃣ Dependency check: leave_request → leave_balance
        if ("leave_request".equalsIgnoreCase(tableName)) {
            String employeeId = (String) record.get("employee_id");
            String leaveTypeId = (String) record.get("leave_type_id");
            int year = LocalDate.now().getYear();

            LeaveBalance balance = leaveBalanceRepo
                    .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
            if (balance != null && isLocked("leave_balance", balance.getBalanceId())) {
                return "Cannot apply/edit leave. Leave balance is being edited by "
                        + getLockedBy("leave_balance", balance.getBalanceId());
            }
        }

        // 3️⃣ Dependency check: leave_balance → leave_request
        if ("leave_balance".equalsIgnoreCase(tableName)) {
            String employeeId = (String) record.get("employee_id");
            String leaveTypeId = (String) record.get("leave_type_id");
            int year = (Integer) record.get("year");

            // Check if any related leave_request is locked
            List<Map<String, Object>> requests = jdbcTemplate.queryForList(
                    "SELECT leave_id FROM leave_request WHERE employee_id = ? AND leave_type_id = ? AND year = ?",
                    employeeId, leaveTypeId, year
            );

            for (Map<String, Object> req : requests) {
                String leaveId = (String) req.get("leave_id");
                if (isLocked("leave_request", leaveId)) {
                    return "Cannot edit leave balance. Leave request " + leaveId
                            + " is being edited by " + getLockedBy("leave_request", leaveId);
                }
            }
        }

        // 4️⃣ Check if this record is already locked
        Optional<RecordLock> existingLockOpt = lockRepository.findByTableNameAndRecordId(tableName, recordId);
        if (existingLockOpt.isPresent()) {
            RecordLock lock = existingLockOpt.get();
            if (!lock.isExpired()) {
                return "Record is currently being edited by " + lock.getLockedBy();
            }
            lockRepository.delete(lock); // remove expired lock
        }

        // 5️⃣ Acquire new lock
        lockRepository.save(RecordLock.builder()
                .tableName(tableName)
                .recordId(recordId)
                .lockedBy(lockedBy)
                .employeeId((String) record.get("employee_id"))
                .lockedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(LOCK_EXPIRY_MINUTES))
                .build());

        return "Lock acquired successfully";
    }


    @Override
    public synchronized void releaseLock(String tableName, String recordId, String lockedBy) {
        lockRepository.findByTableNameAndRecordId(tableName, recordId)
                .filter(lock -> lock.getLockedBy().equals(lockedBy) || lock.isExpired())
                .ifPresent(lockRepository::delete);
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


    private Map<String, Object> getRecord(String tableName, String recordId) {
        String pkColumn = tablePkMap.get(tableName.toLowerCase());
        if (pkColumn == null)
            throw new RuntimeException("Unknown table: " + tableName);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                String.format("SELECT * FROM %s WHERE %s = ?", tableName, pkColumn), recordId);
        return results.isEmpty() ? null : results.get(0);
    }


    @Transactional
    public void cleanupExpiredLocks() {
        lockRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

}
