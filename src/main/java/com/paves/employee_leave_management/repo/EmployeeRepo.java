package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepo extends JpaRepository<Employee, String> {
    Optional<Employee> findByEmployeeId(String id);

    List<Employee> findByRole(String role);
}
