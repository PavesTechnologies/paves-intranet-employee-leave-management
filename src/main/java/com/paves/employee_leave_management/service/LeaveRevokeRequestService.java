package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveRevokeDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.enums.LeaveRevokeStatus;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveRevokeRepo;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRevokeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveRevokeRequestService implements LeaveRevokeRequest {

    @Autowired
    private final LeaveRevokeRepo leaveRevokeRepo;

    @Autowired
    private final LeaveRequestRepo leaveRequestRepo;

    @Autowired
    private final LeaveBalanceServiceImple leaveBalanceService;

    @Autowired
    private final EmailServiceInterface emailService;

    public LeaveRevokeRequestService(LeaveRevokeRepo leaveRevokeRepo,
                                     LeaveRequestRepo leaveRequestRepo,
                                     LeaveBalanceServiceImple leaveBalanceService,
                                     EmailServiceInterface emailService) {
        this.leaveRevokeRepo = leaveRevokeRepo;
        this.leaveRequestRepo = leaveRequestRepo;
        this.leaveBalanceService = leaveBalanceService;
        this.emailService = emailService;
    }


    @Override
    public String newRevokeRequest(LeaveRevoke revokeRequest) {
        Optional<LeaveRequest> leaveRequest = leaveRequestRepo.findById(revokeRequest.getLeaveRequestId());

        if (leaveRequest.isEmpty()) {
            throw new RuntimeException("Leave Type not found");
        }

        LeaveRevoke request = leaveRevokeRepo.findByLeaveRequestId(revokeRequest.getLeaveRequestId());
        if (request != null && request.getStatus() == LeaveRevokeStatus.PENDING || request.getStatus() == LeaveRevokeStatus.APPROVED) {
            throw new RuntimeException("Leave revoke request already exists");
        }
        revokeRequest.setStatus(LeaveRevokeStatus.PENDING);
        revokeRequest.setManagerId(leaveRequest.get().getEmployee().getManager().getEmployeeId());
        leaveRevokeRepo.save(revokeRequest);
        return "Leave revoke request submitted successfully";
    }

    @Override
    public void approveRequest(String id) {
        System.out.println("id: " + id);
        LeaveRevoke revokeRequest = leaveRevokeRepo.findById(id.trim()).orElseThrow(
                () -> {
                    throw new RuntimeException("Leave revoke request not found");
                }
        );
        System.out.println(revokeRequest);
        Optional<LeaveRequest> leaveRequest = leaveRequestRepo.findByLeaveId(revokeRequest.getLeaveRequestId());
        if (leaveRequest.isEmpty()) {
            throw new RuntimeException("Leave request not found");
        }

        LeaveRequest request = leaveRequest.get();
        request.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepo.save(request);

        leaveBalanceService.updateLeaveBalanceAfterRejected(
                request.getEmployee().getEmployeeId(),
                request.getLeaveType().getLeaveTypeId(),
                request.getDaysRequested(),
                request.getRequestDate().getYear());

        revokeRequest.setStatus(LeaveRevokeStatus.APPROVED);
        leaveRevokeRepo.save(revokeRequest);

        try {
            if (request.getEmployee().getEmail() != null) {
                emailService.sendLeaveRevokeNotification(
                        request.getEmployee().getEmail(),
                        request.getEmployee().getFullName(),
                        request.getLeaveType().getLeaveName(),
                        request.getStartDate().toString(),
                        request.getEndDate().toString()
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to send revoke email: " + e.getMessage());
        }
    }

    @Override
    public List<LeaveRevokeDTO> getPendingRequests(String managerId) {
        List<LeaveRevoke> leaveRevokeList = leaveRevokeRepo.findByManagerIdAndStatus(managerId, LeaveRevokeStatus.PENDING);
        List<LeaveRevokeDTO> leaveRevokeDTOList = new ArrayList<>();
        leaveRevokeList.forEach(leaveRevoke -> {
            Optional<LeaveRequest> leaveRequest = leaveRequestRepo.findByLeaveId(leaveRevoke.getLeaveRequestId());
            LeaveRevokeDTO leaveRevokeDTO = new LeaveRevokeDTO();
            leaveRevokeDTO.setRevokeId(leaveRevoke.getId());
            leaveRevokeDTO.setLeaveRequestId(leaveRevoke.getLeaveRequestId());
            leaveRevokeDTO.setEmployeeId(leaveRequest.get().getEmployee().getEmployeeId());
            leaveRevokeDTO.setEmployeeName(leaveRequest.get().getEmployee().getFullName());
            leaveRevokeDTO.setStartDate(leaveRequest.get().getStartDate());
            leaveRevokeDTO.setEndDate(leaveRequest.get().getEndDate());
            leaveRevokeDTO.setDays(leaveRequest.get().getDaysRequested());
            leaveRevokeDTO.setStatus(leaveRevoke.getStatus());
            leaveRevokeDTO.setReason(leaveRevoke.getReason());
            leaveRevokeDTO.setLeaveName(leaveRequest.get().getLeaveType().getLeaveName());
            leaveRevokeDTOList.add(leaveRevokeDTO);
        });
        return leaveRevokeDTOList;
    }

    @Override
    public void rejectRequest(String id) {
        LeaveRevoke revokeRequest = leaveRevokeRepo.findById(id.trim()).orElseThrow(
                () -> {
                    throw new RuntimeException("Leave revoke request not found");
                }
        );
        revokeRequest.setStatus(LeaveRevokeStatus.REJECTED);
        leaveRevokeRepo.save(revokeRequest);
    }


}
