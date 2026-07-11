package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ScheduledLeaveTypeUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledLeaveTypeUpdateRepo extends JpaRepository<ScheduledLeaveTypeUpdate, String> {

    Optional<ScheduledLeaveTypeUpdate> findByLeaveTypeIdAndStatus(
            String leaveTypeId, ScheduledLeaveTypeUpdate.Status status);

    List<ScheduledLeaveTypeUpdate> findByStatusAndEffectiveDateLessThanEqual(
            ScheduledLeaveTypeUpdate.Status status, LocalDate effectiveDate);

    List<ScheduledLeaveTypeUpdate> findAllByOrderByCreatedAtDesc();

    List<ScheduledLeaveTypeUpdate> findByStatusOrderByCreatedAtDesc(ScheduledLeaveTypeUpdate.Status status);
}
