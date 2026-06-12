package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, String>,
        JpaSpecificationExecutor<LeaveRequest> {
}
