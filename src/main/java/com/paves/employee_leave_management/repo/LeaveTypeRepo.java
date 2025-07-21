package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveTypeRepo extends JpaRepository<LeaveType,String> {
}
