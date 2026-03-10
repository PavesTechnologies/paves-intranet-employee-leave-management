package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepo extends JpaRepository<LeaveBalance, String> {

    boolean existsByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(String empId, String leaveTypeId, int year);

    List<LeaveBalance> findByLeaveTypeLeaveTypeId(String leaveId);

    List<LeaveBalance> findByEmployeeEmployeeId(String employeeId);

    Optional<LeaveBalance> findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);

    Optional<LeaveBalance> findByEmployee_EmployeeIdAndLeaveType_LeaveTypeId(String employeeId, String leaveTypeId);

    LeaveBalance findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);

    List<LeaveBalance> findAllByYear(int year);

    List<LeaveBalance> findByLeaveType(LeaveType savedLeaveType);

    void deleteByLeaveType(LeaveType leaveType);

    Optional<LeaveBalance> findByEmployeeAndLeaveType(Employee employee, LeaveType leaveType);

    @Query("SELECT lb FROM LeaveBalance lb JOIN FETCH lb.leaveType WHERE lb.employee.employeeId = :employeeId")
    List<LeaveBalance> findByEmployeeEmployeeIdWithLeaveType(@Param("employeeId") String employeeId);
//    Optional<LeaveBalance> findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);

    // Search leave balances by employeeId, firstName, or lastName
    @Query("SELECT lb FROM LeaveBalance lb " +
            "WHERE LOWER(lb.employee.employeeId) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(lb.employee.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(lb.employee.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<LeaveBalance> searchByEmployee(@Param("query") String query);

    // Autocomplete for names or employeeIds
    @Query("SELECT DISTINCT CONCAT(lb.employee.firstName, ' ', lb.employee.lastName) FROM LeaveBalance lb " +
            "WHERE LOWER(lb.employee.employeeId) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(lb.employee.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(lb.employee.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<String> autocompleteEmployee(@Param("query") String query);

    List<LeaveBalance> findByEmployee_EmployeeIdAndYear(String employeeId, Integer year);

    LeaveBalance getByEmployeeIdAndLeaveType_LeaveTypeId(String employeeId, String leaveTypeId);

    boolean existsByBlockId(String blockId);

    LeaveBalance getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);
//    LeaveBalance getByEmployeeIdAndLeaveTypeAndYear(String employeeId, String LeaveType, Integer year);

    List<LeaveBalance> getByEmployeeIdAndLeaveType_LeaveTypeIdAndYearAndBlockId(String employeeId, String leaveTypeId, Integer year, String blockId);

    List<LeaveBalance> findByBlockId(String blockId);

    List<LeaveBalance> findByEmployeeIdAndLeaveType_LeaveTypeIdAndYearAndBlockId(String employeeId, String leaveTypeId, Integer year, String blockId);

    List<LeaveBalance> findByEmployeeIdAndBlockId(String employeeId, String blockId);

    @Modifying
    @Transactional
    @Query("UPDATE LeaveBalance lb SET lb.blockId = null, lb.isBlocked = false WHERE lb.employeeId = :empId AND lb.blockId = :blockId AND lb.leaveType.leaveTypeId IN :leaveTypeIds")
    int unblockBalancesForEmployeeAndTypes(
            @Param("empId") String employeeId,
            @Param("blockId") String blockId,
            @Param("leaveTypeIds") List<String> leaveTypeIds
    );

    List<LeaveBalance> findAllByYearAndLeaveTypeLeaveTypeId(int i, String leaveTypeId);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(String empId, String leaveTypeId, Integer year);
}
