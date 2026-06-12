package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.GenderBasedLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenderBasedRepo extends JpaRepository<GenderBasedLeave, String> {
    Optional<GenderBasedLeave> findByLeaveNameIgnoreCase(String leaveName);
    Optional<GenderBasedLeave> findByLeaveTypeId(String leaveTypeId);
    List<GenderBasedLeave> findByActiveTrue();
}
