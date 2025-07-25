package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveStatus;

import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveValidationServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
//@RequiredArgsConstructor
public class LeaveRequestServiceImple implements LeaveRequestServiceInterface {

    @Autowired
    LeaveRequestRepo leaveRequestRepo;

    @Autowired
    EmployeeRepo employeeRepo;

    @Autowired
    LeaveTypeRepo leaveTypeRepo;

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceServiceInterface;

    @Override
    public List<LeaveRequest> getPendingRequestsForManager(String managerId) {
        return leaveRequestRepo.findByStatusAndEmployee_Manager_EmployeeId(LeaveStatus.PENDING , managerId);
    }

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceService;

    @Override
    public List<LeaveRequest> getLeaveHistoryForManager(String managerId) {
        return leaveRequestRepo.findByEmployee_Manager_EmployeeId(managerId);
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

//        leaveBalanceServiceInterface.updateLeaveBalanceAfterApproval(request.getEmployee().getEmployeeId(),request.get);
        leaveBalanceService.updateLeaveBalanceAfterApproval(request.getEmployee().getEmployeeId(), request.getLeaveType().getLeaveTypeId(), request.getDaysRequested());

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
    public LeaveRequest updateLeaveRequestByManager(String leaveId, String managerId, String leaveTypeId, LocalDate startDate, LocalDate endDate) {
        LeaveRequest request = leaveRequestRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!request.getEmployee().getManager().getEmployeeId().equals(managerId)) {
            throw new RuntimeException("Unauthorized action");
        }

        if (leaveTypeId != null) {
            LeaveType newType = leaveTypeRepo.findById(leaveTypeId)
                    .orElseThrow(() -> new RuntimeException("Leave type not found"));
            request.setLeaveType(newType);
        }

        if (startDate != null && endDate != null) {
            request.setStartDate(startDate);
            request.setEndDate(endDate);
            request.setDaysRequested((int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }

        return leaveRequestRepo.save(request);
    }


    @Autowired
    LeaveValidationServiceInterface leaveValidationService;

    @Override
    public ValidationResultDTO updateRequest(LeaveRequest leaveRequest) {
        return leaveRequestRepo.findByLeaveIdAndEmployee_EmployeeId(leaveRequest.getLeaveId(), leaveRequest.getEmployee().getEmployeeId()).map(existingRequest -> {
            if(existingRequest.getStatus().equals(LeaveStatus.APPROVED) || existingRequest.getStatus().equals(LeaveStatus.REJECTED)) {
                throw new LeaveBalanceExceptionHandler("Cannot update a leave request that has already been approved or rejected.");
            }
            LeaveRequestValidationDTO validationDTO = LeaveRequestValidationDTO.builder()
                    .employeeId(existingRequest.getEmployee().getEmployeeId())
                    .leaveTypeId(existingRequest.getLeaveType().getLeaveTypeId())
                    .startDate(existingRequest.getStartDate())
                    .endDate(existingRequest.getEndDate())
                    .daysRequested(existingRequest.getDaysRequested())
                    .reason(existingRequest.getReason())
                    .build();
            return leaveValidationService.validateLeaveRequest(validationDTO);
        }).orElseThrow(() -> new RuntimeException("Leave request not found"));
    }
}
