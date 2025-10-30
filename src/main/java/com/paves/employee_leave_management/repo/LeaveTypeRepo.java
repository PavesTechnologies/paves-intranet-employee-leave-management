package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepo extends JpaRepository<LeaveType,String> {
    Optional<LeaveType> findByLeaveName(String leaveName);

    Optional<LeaveType> findByLeaveTypeId(String leaveTypeId);

    List<LeaveType> findByActiveTrue();

    Optional<LeaveType> findByLeaveNameIgnoreCase(String leaveName);

//    Optional<LeaveType> findByLeaveName(String leaveName);
}
