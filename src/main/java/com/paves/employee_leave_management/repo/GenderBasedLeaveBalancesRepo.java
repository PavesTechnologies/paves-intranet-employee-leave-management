package com.paves.employee_leave_management.repo;


import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenderBasedLeaveBalancesRepo extends JpaRepository<GenderBasedLeaveBalance, String> {

    Optional<GenderBasedLeaveBalance> findByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);
    List<GenderBasedLeaveBalance> findByEmployeeIdAndYear(String employeeId, int year);
    GenderBasedLeaveBalance findByYearAndEmployeeId(int year, String employeeId);

    void deleteByLeaveType_LeaveTypeId(String leaveTypeId);
    List<GenderBasedLeaveBalance> findAllByYear(int year);

    List<GenderBasedLeaveBalance> findAllByYearAndIsDeletedFalse(Integer year);
    List<GenderBasedLeaveBalance> findByEmployeeId(String employeeId);
//    GenderBasedLeaveBalance findByEmployeeIdAndLeaveTypeIdAndYear(String employeeId, String leaveTypeId, Integer year);

    // ADMIN
    @Query("SELECT gb FROM GenderBasedLeaveBalance gb " +
            "WHERE gb.year = :year " +
            "AND (gb.isDeleted = false OR gb.isDeleted IS NULL)")
    List<GenderBasedLeaveBalance> findAllByYearAndNotDeleted(@Param("year") Integer year);



    // HR
    @Query("SELECT gb FROM GenderBasedLeaveBalance gb " +
            "WHERE gb.year = :year " +
            "AND (gb.isDeleted = false OR gb.isDeleted IS NULL) " +
            "AND gb.employeeId IN (" +
            "SELECT e.employeeId FROM Employee e " +
            "WHERE e.hr.employeeId = :hrId" +
            ")")
    List<GenderBasedLeaveBalance> findAllByYearAndHrId(
            @Param("year") Integer year,
            @Param("hrId") String hrId);
}
