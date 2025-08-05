package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.LeaveType;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface LeaveTypeServiceInterface {
    public ResponseEntity<LeaveType> addLeaveType(LeaveType leaveType);
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes();
    public ResponseEntity<ApiResponse<LeaveType>> updateLeaveType(String leaveTypeId);
//    public ResponseEntity<LeaveType> getleaveTypeById(String leaveTypeId);

    ResponseEntity<LeaveType> getLeaveTypeById(String leaveTypeId);
}
