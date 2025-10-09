package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.RecordLock;
import com.paves.employee_leave_management.repo.RecordLockRepository;
import com.paves.employee_leave_management.serviceInterface.RecordLockServiceInterface;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordLockServiceImple implements RecordLockServiceInterface {
    private final RecordLockRepository lockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

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
                if (pkRs.next()) {
                    String pkColumn = pkRs.getString("COLUMN_NAME");
                    tablePkMap.put(tableName.toLowerCase(), pkColumn);
                }
            }
        }
        System.out.println("Primary keys loaded: " + tablePkMap);
    }

    @Override
    public synchronized String lockRecord(String tableName, String recordId, String lockedBy) {
        if (!recordExists(tableName, recordId)) return "Record does not exist";

        Optional<RecordLock> existingLockOpt = lockRepository.findByTableNameAndRecordId(tableName, recordId);

        if (existingLockOpt.isPresent()) {
            RecordLock existingLock = existingLockOpt.get();
            if (!existingLock.isExpired()) {
                return "Record is currently being edited by " + existingLock.getLockedBy();
            } else {
                lockRepository.delete(existingLock); // remove expired lock
            }
        }

        RecordLock newLock = RecordLock.builder()
                .tableName(tableName)
                .recordId(recordId)
                .lockedBy(lockedBy)
                .lockedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(LOCK_EXPIRY_MINUTES))
                .build();

        lockRepository.save(newLock);
        return "Lock acquired successfully";
    }

    @Override
    public synchronized void releaseLock(String tableName, String recordId, String lockedBy) {
        Optional<RecordLock> existingLockOpt = lockRepository.findByTableNameAndRecordId(tableName, recordId);
        existingLockOpt.ifPresent(lock -> {
            if (lock.getLockedBy().equals(lockedBy) || lock.isExpired()) {
                lockRepository.delete(lock);
            }
        });
    }

    @Override
    public boolean isLocked(String tableName, String recordId) {
        Optional<RecordLock> existingLockOpt = lockRepository.findByTableNameAndRecordId(tableName, recordId);
        return existingLockOpt.isPresent() && !existingLockOpt.get().isExpired();
    }

    @Override
    public String getLockedBy(String tableName, String recordId) {
        Optional<RecordLock> existingLockOpt = lockRepository.findByTableNameAndRecordId(tableName, recordId);
        return existingLockOpt.map(RecordLock::getLockedBy).orElse(null);
    }

    private boolean recordExists(String tableName, String recordId) {
        String pkColumn = tablePkMap.get(tableName.toLowerCase());
        if (pkColumn == null) throw new RuntimeException("Unknown table: " + tableName);

        String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", tableName, pkColumn);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, recordId);
        return count != null && count > 0;
    }

    // Cleanup expired locks every 5 minutes
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void cleanupExpiredLocks() {
        int deleted = lockRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
