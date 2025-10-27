package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.enums.BlockStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveBlockRepo extends JpaRepository<LeaveBlock, String> {

    List<LeaveBlock> findByStatusAndStartDateLessThanEqual(BlockStatus status, LocalDate date);

    List<LeaveBlock> findByStatusAndEndDateBefore(BlockStatus status, LocalDate date);

    List<LeaveBlock> findByManagerIdAndStatus(String managerId, BlockStatus status);

    @Query("""
    SELECT CASE WHEN COUNT(lb) > 0 THEN TRUE ELSE FALSE END
    FROM LeaveBlock lb
    WHERE lb.projectId = :projectId
      AND lb.startDate <= :endDate
      AND lb.endDate >= :startDate
""")
    boolean existsByProjectIdAndDateRangeOverlap(@Param("projectId") String projectId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

}
