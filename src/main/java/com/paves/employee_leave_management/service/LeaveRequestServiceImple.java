package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveRequestValidationDTO;
import com.paves.employee_leave_management.dto.ValidationResultDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveStatus;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRequestServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveValidationServiceInterface;
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

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceService;

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

    @Autowired
    LeaveApplicationService leaveApplicationService;

    @Autowired
    LeaveValidationServiceInterface leaveValidationService;

    @Override
    public LeaveRequest updateRequest(LeaveRequest leaveRequest) {
        return leaveRequestRepo.findById(leaveRequest.getLeaveId()).map(existingRequest -> {
            if(existingRequest.getStatus().equals(LeaveStatus.PENDING)){
                LeaveRequestValidationDTO leaveRequestValidationDTO = LeaveRequestValidationDTO.builder()
                        .employeeId(leaveRequest.getEmployee().getEmployeeId())
                        .leaveTypeId(leaveRequest.getLeaveType().getLeaveTypeId())
                        .startDate(leaveRequest.getStartDate())
                        .endDate(leaveRequest.getEndDate())
                        .daysRequested(leaveRequest.getDaysRequested())
                        .reason(leaveRequest.getReason())
                        .build();
                ValidationResultDTO validationResult = leaveValidationService.validateLeaveRequest(leaveRequestValidationDTO);
                if(validationResult.isValid()) {
                    return leaveRequestRepo.save(leaveRequest);
                } else {
                    throw new LeaveBalanceExceptionHandler(validationResult.getErrors().get(0));
                }
            } else {
                throw new LeaveBalanceExceptionHandler("Leave request is not in pending state");
            }
        }).orElseThrow(() -> new LeaveBalanceExceptionHandler("Leave request not found"));
    }
}
