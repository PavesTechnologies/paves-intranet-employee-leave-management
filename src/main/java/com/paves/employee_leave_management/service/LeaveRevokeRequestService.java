package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveRevokeDTO;
import com.paves.employee_leave_management.dto.RevokeRequestDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.entities.LeaveRevoke;
import com.paves.employee_leave_management.enums.LeaveRevokeStatus;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import com.paves.employee_leave_management.repo.LeaveRevokeRepo;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.GenderBasedLeaveBalanceServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveRevokeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private final LeaveRequestService leaveRequestService;

    @Autowired
    private final GenderBasedLeaveBalanceServiceInterface genderBasedLeaveBalanceServiceInterface;

    public LeaveRevokeRequestService(LeaveRevokeRepo leaveRevokeRepo,
                                     LeaveRequestRepo leaveRequestRepo,
                                     LeaveBalanceServiceImple leaveBalanceService,
                                     EmailServiceInterface emailService,
                                     LeaveRequestService leaveRequestService,
                                     GenderBasedLeaveBalanceServiceInterface genderBasedLeaveBalanceServiceInterface) {
        this.leaveRevokeRepo = leaveRevokeRepo;
        this.leaveRequestRepo = leaveRequestRepo;
        this.leaveBalanceService = leaveBalanceService;
        this.emailService = emailService;
        this.leaveRequestService = leaveRequestService;
        this.genderBasedLeaveBalanceServiceInterface = genderBasedLeaveBalanceServiceInterface;
    }


    @Override
    public String newRevokeRequest(LeaveRevoke revokeRequest) {
        Optional<LeaveRequest> leaveRequest = leaveRequestRepo.findById(revokeRequest.getLeaveRequestId());

        if (leaveRequest.isEmpty()) {
            throw new RuntimeException("Leave Type not found");
        }

        List<LeaveRevoke> requests = leaveRevokeRepo.findByLeaveRequestId(revokeRequest.getLeaveRequestId());
        if (requests != null) {
            for(LeaveRevoke request:requests){
                if (request.getStatus() == LeaveRevokeStatus.PENDING || request.getStatus() == LeaveRevokeStatus.APPROVED) {
                    throw new RuntimeException("Leave revoke request already exists");
                }
            }
        }
        revokeRequest.setStatus(LeaveRevokeStatus.PENDING);
        revokeRequest.setManagerId(leaveRequest.get().getEmployee().getManager().getEmployeeId());
        leaveRevokeRepo.save(revokeRequest);
        return "Leave revoke request submitted successfully";
    }

    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "employeeLeaveBalance", key = "#revokeRequestDTO.employeeId + '-' + #revokeRequestDTO.year"),
                    @CacheEvict(value = "leaveRequestsByEmployeeAndYear", key = "#revokeRequestDTO.employeeId + '-' + #revokeRequestDTO.year"),
            }
    )
    public void approveRequest(String id, RevokeRequestDTO revokeRequestDTO) {
        System.out.println("id: " + id);
        LeaveRevoke revokeRequest = leaveRevokeRepo.findById(id.trim()).orElseThrow(() -> {
            throw new RuntimeException("Leave revoke request not found");
        });
        System.out.println(revokeRequest);
        Optional<LeaveRequest> leaveRequest = leaveRequestRepo.findByLeaveId(revokeRequest.getLeaveRequestId());
        if (leaveRequest.isEmpty()) {
            throw new RuntimeException("Leave request not found");
        }

        LeaveRequest request = leaveRequest.get();
        request.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepo.save(request);



        if(request.getLeaveType()!=null){
            leaveBalanceService.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear());
        }else{
            genderBasedLeaveBalanceServiceInterface.updateLeaveBalanceAfterRejected(
                    request.getEmployee().getEmployeeId(),
                    request.getGenderBasedLeaveType().getLeaveTypeId(),
                    request.getDaysRequested(),
                    request.getRequestDate().getYear());
        }



        revokeRequest.setStatus(LeaveRevokeStatus.APPROVED);
        leaveRevokeRepo.save(revokeRequest);

        try {
            if (request.getEmployee().getEmail() != null) {
                emailService.sendLeaveRevokeNotification(request.getEmployee().getEmail(), request.getEmployee().getFullName(), request.getLeaveType().getLeaveName(), request.getStartDate().toString(), request.getEndDate().toString());
            }
        } catch (Exception e) {
            System.err.println("Failed to send revoke email: " + e.getMessage());
        }
    }

    @Override
    public List<LeaveRevokeDTO> getPendingRequests(String managerId) {
        List<LeaveRevoke> leaveRevokeList = leaveRevokeRepo.findByManagerIdAndStatus(managerId, LeaveRevokeStatus.PENDING);
        if(leaveRevokeList.isEmpty()){
            return new ArrayList<>();
        }
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
            leaveRevokeDTO.setLeaveName(leaveRequestService.resolveLeaveLabel(leaveRequest.get().getResolvedLeaveName()));
            leaveRevokeDTOList.add(leaveRevokeDTO);
        });
        return leaveRevokeDTOList;
    }

    @Override
    public void rejectRequest(String id) {
        LeaveRevoke revokeRequest = leaveRevokeRepo.findById(id.trim()).orElseThrow(() -> {
            throw new RuntimeException("Leave revoke request not found");
        });
        revokeRequest.setStatus(LeaveRevokeStatus.REJECTED);
        leaveRevokeRepo.save(revokeRequest);
    }


}
