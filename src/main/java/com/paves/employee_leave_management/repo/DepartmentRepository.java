package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {

    // Fetch department by name
    Optional<Department> findByName(String name);

    // Fetch department by head (for approval resolution)
    Optional<Department> findByHeadEmployeeId(String headId);
}
