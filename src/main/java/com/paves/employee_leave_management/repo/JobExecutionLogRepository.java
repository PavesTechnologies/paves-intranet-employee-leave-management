package com.paves.employee_leave_management.repositories;

import com.paves.employee_leave_management.entities.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, UUID> {
    int deleteByStartTimeBefore(LocalDateTime cutoffDate);
}
