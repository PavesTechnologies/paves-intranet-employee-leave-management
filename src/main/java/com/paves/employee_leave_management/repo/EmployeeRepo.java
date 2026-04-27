package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepo extends JpaRepository<Employee, String> {
    Optional<Employee> findByEmployeeId(String id);

    @Override
    Page<Employee> findAll(Pageable pageable);

    Page<Employee> findByFullNameContainingIgnoreCase(String search, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
            "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(e.employeeId) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (e.manager.employeeId = :currentUserId " +
            "OR e.hr.employeeId = :currentUserId " +
            "OR e.hrAdministrator.employeeId = :currentUserId)")
    Page<Employee> searchManagedEmployees(
            @Param("search") String search,
            @Param("currentUserId") String currentUserId,
            Pageable pageable
    );
}
