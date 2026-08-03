package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.enums.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query("SELECT e FROM Employee e WHERE e.manager IS NULL AND e.managerId = :managerId")
    List<Employee> findByManagerIsNullAndManagerId(@Param("managerId") String managerId);

    // find employees waiting for their HR to arrive
    @Query("SELECT e FROM Employee e WHERE e.hr IS NULL AND e.hrId = :hrId")
    List<Employee> findByHrIsNullAndHrId(@Param("hrId") String hrId);

    Optional<Employee> findByEmployeeUuid(String employeeUuid);

    List<Employee> findByEmployeeIdInAndStatus(List<String> employeeIds, EmployeeStatus status);
}
