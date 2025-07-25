package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepo extends JpaRepository<LeaveRequest,String> {
    List<LeaveRequest> findByStatusAndEmployee_Manager_EmployeeId(LeaveStatus status, String managerId);
    List<LeaveRequest> findByEmployee_Manager_EmployeeId(String managerId);

}
