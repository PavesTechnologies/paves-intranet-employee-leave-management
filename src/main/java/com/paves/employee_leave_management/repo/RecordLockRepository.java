package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.RecordLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecordLockRepository extends JpaRepository<RecordLock, Long> {
    Optional<RecordLock> findByTableNameAndRecordId(String tableName, String recordId);

    int deleteByExpiresAtBefore(java.time.LocalDateTime time);
    boolean existsByRecordIdAndEmployeeId(String recordId,String employeeId);
}
