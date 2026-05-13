package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.CdcFailureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CdcFailureLogRepository extends JpaRepository<CdcFailureLog, String> {

    // fetch all retryable failures — not yet exhausted
    @Query("SELECT f FROM CdcFailureLog f " +
            "WHERE f.status IN ('FAILED', 'RETRYING') " +
            "AND f.retryCount < f.maxRetries " +
            "ORDER BY f.createdAt ASC")
    List<CdcFailureLog> findRetryableLogs();

    // fetch all exhausted failures for reporting
    List<CdcFailureLog> findByStatusOrderByCreatedAtDesc(
            CdcFailureLog.CdcFailureStatus status);

    // check if a specific event already has a failure log
    boolean existsByEmployeeUuidAndFailureTypeAndStatus(
            String employeeUuid,
            CdcFailureLog.FailureType failureType,
            CdcFailureLog.CdcFailureStatus status);
}