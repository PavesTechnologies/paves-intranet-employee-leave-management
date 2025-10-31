package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.entities.LeaveBlockMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveBlockMemberRepo extends JpaRepository<LeaveBlockMember, String> {

    void deleteByLeaveBlock_Id(String id);

    void deleteAllByLeaveBlock(LeaveBlock leaveBlock);

    List<LeaveBlockMember> findByLeaveBlock(LeaveBlock leaveBlock);

    void deleteByLeaveBlockIdAndEmployeeId(String blockId, String employeeId);
}
