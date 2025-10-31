package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.entities.LeaveBlockLeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveBlockLeaveTypeRepo extends JpaRepository<LeaveBlockLeaveType, String> {

    void deleteByLeaveBlock_Id(String id);

    void deleteAllByLeaveBlock(LeaveBlock leaveBlock);

    List<LeaveBlockLeaveType> findByLeaveBlock(LeaveBlock leaveBlock);
}
