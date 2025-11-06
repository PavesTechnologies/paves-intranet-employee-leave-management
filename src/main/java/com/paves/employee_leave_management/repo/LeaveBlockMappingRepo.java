package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.entities.LeaveBlockLeaveType;
import com.paves.employee_leave_management.entities.LeaveBlockMapping;
import com.paves.employee_leave_management.enums.BlockStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveBlockMappingRepo extends JpaRepository<LeaveBlockMapping, String> {

    void deleteAllByLeaveBlock(LeaveBlock leaveBlock);
    void deleteByLeaveBlockIdAndEmployeeIdAndLeaveTypeIdIn(String id, String employeeId, List<String> leaveTypeIds);
    boolean existsByLeaveBlockIdAndEmployeeId(String blockId, String employeeId);
    boolean existsByLeaveBlockIdAndLeaveTypeId(String blockId, String leaveTypeId);

    void deleteByLeaveBlockIdAndLeaveTypeId(String blockId, String leaveTypeId);
    boolean existsByLeaveBlockIdAndEmployeeIdAndLeaveTypeId(String blockId, String employeeId, String leaveTypeId);
    boolean existsByLeaveBlockId(String blockId);
    LeaveBlockMapping getByLeaveBlockIdAndEmployeeIdAndLeaveTypeId(String blockId, String employeeId, String leaveTypeId);

    void deleteByLeaveBlockId(String blockId);

    List<LeaveBlockMapping> findByLeaveBlock(LeaveBlock block);


    // LeaveBlockMappingRepository
    @Modifying
    @Query("UPDATE LeaveBlockMapping m SET m.status = :inactiveStatus " +
            "WHERE m.leaveBlock.id = :blockId AND m.employeeId = :employeeId AND m.leaveTypeId IN :leaveTypeIds")
    int markMappingsInactive(@Param("inactiveStatus") BlockStatus inactiveStatus,
                             @Param("blockId") String blockId,
                             @Param("employeeId") String employeeId,
                             @Param("leaveTypeIds") List<String> leaveTypeIds);



}
