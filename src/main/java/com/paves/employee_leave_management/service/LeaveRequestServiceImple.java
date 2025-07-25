package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveStatus;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
//@RequiredArgsConstructor
public class LeaveRequestServiceImple implements LeaveRequestServiceInterface {

    @Autowired
    LeaveRequestRepo leaveRequestRepo;

    @Autowired
    EmployeeRepo employeeRepo;

    @Override
    public List<LeaveRequest> getPendingRequestsForManager(String managerId) {
        return leaveRequestRepo.findByStatusAndEmployee_Manager_EmployeeId(LeaveStatus.PENDING , managerId);
    }

    @Override
    public LeaveRequest approveRequest(String leaveId, String managerId) {
        LeaveRequest request = leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!request.getEmployee().getManager().getEmployeeId().equals(managerId)) {
            throw new RuntimeException("Unauthorized action: not the manager of this employee");
        }

        Employee manager = employeeRepo.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());

        return leaveRequestRepo.save(request);
    }

    @Override
    public LeaveRequest rejectRequest(String leaveId, String managerId, String comment) {
        LeaveRequest request = leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!request.getEmployee().getManager().getEmployeeId().equals(managerId)) {
            throw new RuntimeException("Unauthorized action: not the manager of this employee");
        }

        Employee manager = employeeRepo.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        request.setStatus(LeaveStatus.REJECTED);
        request.setApprovedBy(manager);
        request.setResponseDate(LocalDate.now());
        request.setManagerComment(comment);

        return leaveRequestRepo.save(request);

    }

    @Override
    public LeaveRequest updateRequest(String leaveId, String employeeId, LeaveRequest leaveRequest) {
        return null;
    }
}
