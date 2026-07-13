package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaveRequestCacheMethods {

    @Autowired
    private LeaveRequestRepo leaveRequestRepo;

    @Cacheable(value = "leaveRequestByEmployeeAndYearPendingAndApproved", key = "#employeeId + '-' + #year")
    public List<LeaveRequest> getLeaveRequestByEmployeeAndYearPendingAndApproved(String employeeId, int year) {
        return leaveRequestRepo.findByEmployeeIdAndYearAndApprovedOrPending(employeeId, year);
    }

}
