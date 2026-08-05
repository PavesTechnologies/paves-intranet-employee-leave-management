package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeaveBalanceJobRepository extends JpaRepository<LeaveBalanceJob, String> {
    List<LeaveBalanceJob> findByLeaveTypeIdOrderByStartedAtDesc(String leaveTypeId);

    List<LeaveBalanceJob> findByStatus(LeaveBalanceJob.JobStatus status);

    List<LeaveBalanceJob> findByStatusAndUpdatedAtBefore(LeaveBalanceJob.JobStatus status, LocalDateTime cutoff);
}