package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepo extends JpaRepository<LeaveType, String> {
    Optional<LeaveType> findByLeaveName(String leaveName);

    Optional<LeaveType> findByLeaveTypeId(String leaveTypeId);

    List<LeaveType> findByActiveTrue();

    Optional<LeaveType> findByLeaveNameIgnoreCase(String leaveName);

//    Optional<LeaveType> findByLeaveName(String leaveName);

    @Query("SELECT l FROM LeaveType l WHERE l.active = false AND l.effectiveStartDate <= CURRENT_DATE")
    List<LeaveType> findPendingEffectiveLeaveTypes();

    List<LeaveType> findByActiveTrueAndDeactivationEffectiveDateLessThanEqual(LocalDate date);

}
