package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.GenderBasedLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenderBasedRepo extends JpaRepository<GenderBasedLeave, String> {
    Optional<GenderBasedLeave> findByLeaveNameIgnoreCase(String leaveName);
    Optional<GenderBasedLeave> findByLeaveTypeId(String leaveTypeId);
    List<GenderBasedLeave> findByActiveTrue();
    List<GenderBasedLeave> findByActiveTrueAndEffectiveEndDateLessThanEqual(LocalDate date);

    @Query("SELECT g FROM GenderBasedLeave g WHERE g.active = false AND g.effectiveStartDate <= CURRENT_DATE")
    List<GenderBasedLeave> findPendingEffectiveGenderBasedLeaveTypes();
}
