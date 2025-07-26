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
import java.util.Optional;

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
    List<LeaveRequest> findByEmployee_Manager_EmployeeId(String managerId);
    List<LeaveRequest> findByStatusAndEmployee_Manager_EmployeeId(LeaveStatus status, String managerId);
    List<LeaveRequest> findByEmployee_EmployeeId(String employeeId);

    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE lr.employee.employeeId = :employeeId " +
            "AND lr.leaveType.leaveTypeId = :leaveTypeId " +
            "AND lr.status = 'APPROVED' " +
            "ORDER BY lr.startDate ASC")
    List<LeaveRequest> findApprovedLeavesByType(@Param("employeeId") String employeeId,
                                                @Param("leaveTypeId") String leaveTypeId);

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr " +
            "WHERE lr.employee.employeeId = :employeeId " +
            "AND lr.leaveType.leaveTypeId = :leaveTypeId " +
            "AND lr.status = 'PENDING'")
    int countPendingLeavesByType(@Param("employeeId") String employeeId,
                                 @Param("leaveTypeId") String leaveTypeId);

    Optional<LeaveRequest> findByLeaveIdAndEmployee_EmployeeId(String leaveId, String employeeId);

    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.employee " +
            "JOIN FETCH lr.leaveType " +
            "WHERE lr.leaveId = :leaveId AND lr.employee.employeeId = :employeeId")
    Optional<LeaveRequest> findByLeaveIdAndEmployeeIdWithDetails(@Param("leaveId") String leaveId, 
                                                                 @Param("employeeId") String employeeId);
}
