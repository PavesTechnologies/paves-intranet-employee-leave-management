package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeaveBalanceJobRepository extends JpaRepository<LeaveBalanceJob, String> {
    List<LeaveBalanceJob> findByLeaveTypeIdOrderByStartedAtDesc(String leaveTypeId);

    List<LeaveBalanceJob> findByStatus(LeaveBalanceJob.JobStatus status);

    List<LeaveBalanceJob> findByStatusAndUpdatedAtBefore(LeaveBalanceJob.JobStatus status, LocalDateTime cutoff);

    // Plain conditional UPDATE rather than entity save() — a claim has to work uniformly for
    // rows created before this recovery feature existed (no @Version history to rely on) and
    // gives the same "only one caller wins" guarantee via the row lock taken during the UPDATE.
    // Returns 1 if this call won the claim, 0 if another thread/node already claimed it first.
    @Modifying
    @Query("UPDATE LeaveBalanceJob j SET j.status = :newStatus, j.updatedAt = :now " +
            "WHERE j.jobId = :jobId AND j.status = :expectedStatus")
    int compareAndSetStatus(@Param("jobId") String jobId,
                             @Param("expectedStatus") LeaveBalanceJob.JobStatus expectedStatus,
                             @Param("newStatus") LeaveBalanceJob.JobStatus newStatus,
                             @Param("now") LocalDateTime now);
}