package com.paves.employee_leave_management.service;


import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.dto.LeaveCompoffUpdateStatusDTO;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveStatusCompoff;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveCompoffServiceImpl implements LeaveCompoffSerivceInterface {
    private final LeaveCompoffRepo leaveCompoffRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;

    @Override
    public void requestCompoff(LeaveCompoffRequestDTO dto) {
        LeaveCompoff compoff = LeaveCompoff.builder()
                .employeeId(dto.getEmployeeId())
                .managerId(dto.getManagerId())
                .workedDate(dto.getWorkedDate())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .days(dto.getDays())
                .halfDays(dto.getHalfDays())
                .note(dto.getNote())
                .file(dto.getFile())
                .status(LeaveStatusCompoff.PENDING)
                .build();

        LeaveCompoff cf =leaveCompoffRepo.save(compoff);
        System.out.println(cf);
    }

    @Override
    public void updateCompoffStatus(LeaveCompoffUpdateStatusDTO dto) {
        LeaveCompoff compoff = leaveCompoffRepo.findById(dto.getCompoffId())
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveStatusCompoff newStatus = LeaveStatusCompoff.valueOf(dto.getStatus().toUpperCase());
        compoff.setStatus(newStatus);
        compoff.setActionDate(LocalDate.now());
        leaveCompoffRepo.save(compoff);

        if (newStatus == LeaveStatusCompoff.APPROVED) {
            compoff.setExpiryDate(LocalDate.now().plusDays(21));
            LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(compoff.getEmployeeId(), "L-COMPOFF",LocalDate.now().getYear());
            if (balance == null) {
                throw new RuntimeException("Leave balance record not found for employee: " + compoff.getEmployeeId());
            }

            double days = compoff.getDays();
            balance.setTotalLeaves(balance.getTotalLeaves() + days);
            balance.setRemainingLeaves(balance.getRemainingLeaves() + days);
            balance.setAccruedLeaves(balance.getAccruedLeaves() + days);
            balance.setLastAccrualDate(LocalDate.now());

            leaveBalanceRepo.save(balance);
        }
    }

    @Override
    public List<LeaveCompoff> getCompoffsByEmployee(String employeeId) {
        return leaveCompoffRepo.findByEmployeeId(employeeId);
    }

    @Override
    public List<LeaveCompoff> getCompoffsByManagerAndStatus(String managerId, LeaveStatusCompoff status) {
        return leaveCompoffRepo.findByManagerIdAndStatus(managerId, status);
    }
}