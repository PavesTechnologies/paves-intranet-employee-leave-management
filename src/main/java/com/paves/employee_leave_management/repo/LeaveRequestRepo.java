package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.dto.ManagerQueryDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.LeaveStatus;
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

    //Optional<LeaveRequest> findByLeaveIdAndEmployee_EmployeeId(String leaveId, String employeeId);

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


    @Query("""
    SELECT lr 
    FROM LeaveRequest lr 
    WHERE lr.employee.manager.employeeId = :#{#queryDTO.managerId} 
      AND (
           (:#{#queryDTO.status} IS NULL 
                AND lr.status IN ('APPROVED', 'REJECTED', 'CANCELLED', 'PENDING')
           ) 
           OR lr.status = :#{#queryDTO.status}
      )
      AND (:#{#queryDTO.employeeId} IS NULL OR lr.employee.employeeId = :#{#queryDTO.employeeId})
      AND (:#{#queryDTO.leaveTypeId} IS NULL OR lr.leaveType.leaveTypeId = :#{#queryDTO.leaveTypeId})
      AND (:#{#queryDTO.fromDate} IS NULL OR lr.startDate BETWEEN :#{#queryDTO.fromDate} AND :#{#queryDTO.toDate})
      AND (:#{#queryDTO.year} IS NULL OR FUNCTION('YEAR', lr.startDate) = :#{#queryDTO.year})
      AND (
           :#{#queryDTO.month} IS NULL 
           OR (
                FUNCTION('MONTH', lr.startDate) = :#{#queryDTO.month}
                AND (:#{#queryDTO.year} IS NULL OR FUNCTION('YEAR', lr.startDate) = :#{#queryDTO.year})
              )
      )""")
    List<LeaveRequest> findManagerHistoryByCriteria(@Param("queryDTO") ManagerQueryDTO queryDTO);

    @Query("""
    SELECT COUNT(lr)
    FROM LeaveRequest lr
    WHERE lr.employee.manager.employeeId = :managerId
      AND lr.status = 'PENDING'
      AND FUNCTION('YEAR', lr.startDate) = FUNCTION('YEAR', CURRENT_DATE)
""")
    long countPendingLeavesByManager(@Param("managerId") String managerId);




    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.employeeId = :empId AND lr.startDate BETWEEN :startDate AND :endDate")
    List<LeaveRequest> findLeaveHistory(@Param("empId") String empId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);




    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE (:employeeId IS NULL OR lr.employee.employeeId = :employeeId) " +
            "AND lr.status IN ('PENDING', 'APPROVED')")
    List<LeaveRequest> findPendingOrApprovedByEmployee(@Param("employeeId") String employeeId);

    List<LeaveRequest> findByEmployee_EmployeeIdAndLeaveType_LeaveNameAndYear(String employeeId, String leaveName, Integer year);
    long countByEmployee_EmployeeIdAndStatus(String employeeId, LeaveStatus status);

    void deleteByLeaveTypeAndStatus(LeaveType leaveType, LeaveStatus status);

    List<LeaveRequest> findByEmployee_EmployeeIdAndStatus(String employeeId, LeaveStatus leaveStatus);
}
