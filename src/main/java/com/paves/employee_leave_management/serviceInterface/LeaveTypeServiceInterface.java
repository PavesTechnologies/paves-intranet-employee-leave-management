package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
//import com.paves.employee_leave_management.dto.LeaveTypeDto;
import com.paves.employee_leave_management.entities.LeaveType;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface LeaveTypeServiceInterface {
    public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType);
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes();
//    public ResponseEntity<ApiResponse<LeaveType>> updateLeaveType(String leaveTypeId);
    @Transactional
    ResponseEntity<LeaveType> updateLeaveType(LeaveType updatedLeaveType);

    ResponseEntity<LeaveType> getLeaveTypeById(String leaveTypeId);

    ResponseEntity<String> deleteLeaveType(String leaveTypeId);
}
