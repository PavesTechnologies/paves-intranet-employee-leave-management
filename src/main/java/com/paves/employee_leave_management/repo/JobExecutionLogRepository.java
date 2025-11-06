package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.JobExecutionLog;
import com.paves.employee_leave_management.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, String> {
    int deleteByStartTimeBefore(LocalDateTime cutoffDate);

    @Modifying
    @Query("UPDATE JobExecutionLog j SET j.status = :status, j.endTime = :endTime, j.durationMs = :duration, j.errorMessage = :errorMessage WHERE j.id = :id")
    void updateJobStatus(
            @Param("id") String id,
            @Param("status") JobStatus status,
            @Param("endTime") LocalDateTime endTime,
            @Param("duration") Long duration,
            @Param("errorMessage") String errorMessage
    );
}
