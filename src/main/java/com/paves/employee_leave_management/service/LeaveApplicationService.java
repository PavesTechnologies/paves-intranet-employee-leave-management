package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class LeaveApplicationService {

    @Autowired
    private LeaveRequestRepo leaveRequestRepository;

    @Autowired
    private EmployeeRepo employeeRepository;

    @Autowired
    private LeaveTypeRepo leaveTypeRepository;

    public LeaveRequest saveLeaveRequest(LeaveRequestValidationDTO request) {
        // Fetch employee and leave type entities
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found with ID: " + request.getEmployeeId()));

        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new RuntimeException("Leave type not found with ID: " + request.getLeaveTypeId()));

        // Calculate days requested if not provided
        int daysRequested = request.getDaysRequested();
        if (daysRequested <= 0) {
            daysRequested = calculateWorkingDays(request.getStartDate(), request.getEndDate());
        }

        // Create and save leave request using the entity constructor
        LeaveRequest leaveRequest = new LeaveRequest(
                employee,
                leaveType,
                request.getStartDate(),
                request.getEndDate(),
                daysRequested,
                request.getReason()
        );

        return leaveRequestRepository.save(leaveRequest);
    }

    private int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        // Simple calculation - you might want to implement more sophisticated logic
        // to exclude weekends and holidays
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}