package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.LeaveType;

import java.util.List;

public interface LeaveTypeServiceInterface {
    public LeaveType addLeaveType(LeaveType leaveType);
    public List<LeaveType> getAllLeaveTypes();
}
