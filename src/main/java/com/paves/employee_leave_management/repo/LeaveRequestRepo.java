package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepo extends JpaRepository<LeaveRequest, String> {

    List<LeaveRequest> findByEmployee(Employee employee);

    List<LeaveRequest> findByEmployeeAndStatus(Employee employee, LeaveStatus status);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.id = :employeeId " +
            "AND lr.status IN ('PENDING', 'APPROVED') " +
            "AND ((lr.startDate BETWEEN :startDate AND :endDate) " +
            "OR (lr.endDate BETWEEN :startDate AND :endDate) " +
            "OR (lr.startDate <= :startDate AND lr.endDate >= :endDate))")
    List<LeaveRequest> findOverlappingLeaves(@Param("employeeId") String employeeId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);
    LeaveRequest findByStatusAndEmployee_Manager_EmployeeId(LeaveStatus status, String managerId);
}