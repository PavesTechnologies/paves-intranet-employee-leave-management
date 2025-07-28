package com.paves.employee_leave_management.repo;

import aj.org.objectweb.asm.commons.Remapper;
import com.paves.employee_leave_management.dto.ManagerQueryDTO;
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
public interface LeaveRequestRepo extends JpaRepository<LeaveRequest, String>{

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.leaveId = :leaveId")
    Optional<LeaveRequest> findById(@Param("leaveId") String leaveId);

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

    Optional<LeaveRequest> findByLeaveIdAndEmployee_Manager_EmployeeId(String leaveId, String managerId);

    Optional<LeaveRequest> findByLeaveIdAndEmployee_EmployeeId(String leaveId, String employeeId);

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


    @Query("SELECT lr FROM LeaveRequest lr " +
            "JOIN FETCH lr.employee " +
            "JOIN FETCH lr.leaveType " +
            "WHERE lr.leaveId = :leaveId AND lr.employee.employeeId = :employeeId")
    Optional<LeaveRequest> findByLeaveIdAndEmployeeIdWithDetails(@Param("leaveId") String leaveId, 
                                                                 @Param("employeeId") String employeeId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.manager.employeeId = :#{#queryDTO.managerId} " +
            "AND (:#{#queryDTO.status} IS NULL AND lr.status = 'PENDING' OR lr.status = :#{#queryDTO.status}) " +
            "AND (:#{#queryDTO.employeeId} IS NULL OR lr.employee.employeeId = :#{#queryDTO.employeeId}) " +
            "AND (:#{#queryDTO.leaveTypeId} IS NULL OR lr.leaveType.leaveTypeId = :#{#queryDTO.leaveTypeId}) " +
            "AND (:#{#queryDTO.fromDate} IS NULL OR lr.startDate BETWEEN :#{#queryDTO.fromDate} AND :#{#queryDTO.toDate})")
    List<LeaveRequest> findManagerRequestsByCriteria(@Param("queryDTO") ManagerQueryDTO queryDTO);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.manager.employeeId = :#{#queryDTO.managerId} " +
            "AND (:#{#queryDTO.status} IS NULL AND lr.status IN ('APPROVED', 'REJECTED', 'CANCELLED' , 'PENDING') OR lr.status = :#{#queryDTO.status}) " +
            "AND (:#{#queryDTO.employeeId} IS NULL OR lr.employee.employeeId = :#{#queryDTO.employeeId}) " +
            "AND (:#{#queryDTO.leaveTypeId} IS NULL OR lr.leaveType.leaveTypeId = :#{#queryDTO.leaveTypeId}) " +
            "AND (:#{#queryDTO.fromDate} IS NULL OR lr.startDate BETWEEN :#{#queryDTO.fromDate} AND :#{#queryDTO.toDate})")
    List<LeaveRequest> findManagerHistoryByCriteria(@Param("queryDTO") ManagerQueryDTO queryDTO);
}
