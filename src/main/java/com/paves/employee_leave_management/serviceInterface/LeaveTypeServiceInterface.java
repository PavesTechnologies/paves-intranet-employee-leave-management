package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.LeaveType;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface LeaveTypeServiceInterface {
    public ResponseEntity<LeaveType> addLeaveType(LeaveType leaveType);
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes();
    public ResponseEntity<LeaveType> updateLeaveType(LeaveType leaveType);
    public ResponseEntity<LeaveType> getLeaveTypeById(String leaveTypeId);

}
