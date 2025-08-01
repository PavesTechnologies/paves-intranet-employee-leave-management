package com.paves.employee_leave_management.service;


import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
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
    public void approveCompoff(Long compoffId) {
        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveStatusCompoff currentStatus = compoff.getStatus();

        // ✅ Allow approve if status is PENDING or REJECTED
        if (currentStatus != LeaveStatusCompoff.PENDING && currentStatus != LeaveStatusCompoff.REJECTED) {
            throw new RuntimeException("Only pending or rejected compoffs can be approved.");
        }

        LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                compoff.getEmployeeId(), "L-COMPOFF", LocalDate.now().getYear());

        if (balance == null) {
            throw new RuntimeException("Leave balance not found for employee: " + compoff.getEmployeeId());
        }

        double days = compoff.getDays();
            balance.setTotalLeaves(balance.getTotalLeaves() + days);
            balance.setRemainingLeaves(balance.getRemainingLeaves() + days);
            balance.setAccruedLeaves(balance.getAccruedLeaves() + days);

        compoff.setStatus(LeaveStatusCompoff.APPROVED);
        compoff.setActionDate(LocalDate.now());
        compoff.setExpiryDate(LocalDate.now().plusDays(21));
        balance.setLastAccrualDate(LocalDate.now());

        leaveCompoffRepo.save(compoff);
        leaveBalanceRepo.save(balance);
    }

    @Override
    public void rejectCompoff(Long compoffId) {
        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveStatusCompoff currentStatus = compoff.getStatus();

        // ✅ Allow reject if status is PENDING or APPROVED
        if (currentStatus != LeaveStatusCompoff.PENDING && currentStatus != LeaveStatusCompoff.APPROVED) {
            throw new RuntimeException("Only pending or approved compoffs can be rejected.");
        }

        // ✅ If APPROVED → REJECTED, subtract from balance
        if (currentStatus == LeaveStatusCompoff.APPROVED) {
            LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                    compoff.getEmployeeId(), "L-COMPOFF", LocalDate.now().getYear());

            if (balance == null) {
                throw new RuntimeException("Leave balance not found for employee: " + compoff.getEmployeeId());
            }

            double days = compoff.getDays();
            balance.setTotalLeaves(Math.max(0, balance.getTotalLeaves() - days));
            balance.setRemainingLeaves(Math.max(0, balance.getRemainingLeaves() - days));
            balance.setAccruedLeaves(Math.max(0, balance.getAccruedLeaves() - days));
            leaveBalanceRepo.save(balance);
        }

        compoff.setStatus(LeaveStatusCompoff.REJECTED);
        compoff.setActionDate(LocalDate.now());
        compoff.setExpiryDate(null); // Optional

        leaveCompoffRepo.save(compoff);
    }

//    @Override
//    public void approveCompoff(Long compoffId) {
//        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
//                .orElseThrow(() -> new RuntimeException("Compoff request not found"));
//
//        if (compoff.getStatus() != LeaveStatusCompoff.PENDING) {
//            throw new RuntimeException("Only pending compoffs can be approved.");
//        }
//
//        compoff.setStatus(LeaveStatusCompoff.APPROVED);
//        compoff.setActionDate(LocalDate.now());
//        compoff.setExpiryDate(LocalDate.now().plusDays(21));
//
//        LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
//                compoff.getEmployeeId(), "L-COMPOFF", LocalDate.now().getYear());
//
//        if (balance == null) {
//            throw new RuntimeException("Leave balance not found for employee: " + compoff.getEmployeeId());
//        }
//
//        double days = compoff.getDays();
//        balance.setTotalLeaves(balance.getTotalLeaves() + days);
//        balance.setRemainingLeaves(balance.getRemainingLeaves() + days);
//        balance.setAccruedLeaves(balance.getAccruedLeaves() + days);
//        balance.setLastAccrualDate(LocalDate.now());
//
//        leaveCompoffRepo.save(compoff);
//        leaveBalanceRepo.save(balance);
//    }


//    @Override
//    public void rejectCompoff(Long compoffId) {
//        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
//                .orElseThrow(() -> new RuntimeException("Compoff request not found"));
//
//        if (compoff.getStatus() != LeaveStatusCompoff.PENDING) {
//            throw new RuntimeException("Only pending compoffs can be rejected.");
//        }
//
//        compoff.setStatus(LeaveStatusCompoff.REJECTED);
//        compoff.setActionDate(LocalDate.now());
//        leaveCompoffRepo.save(compoff);
//    }

    @Override
    public List<LeaveCompoff> getCompoffsByEmployee(String employeeId) {
        return leaveCompoffRepo.findByEmployeeId(employeeId);
    }

    @Override
    public List<LeaveCompoff> getCompoffsByManagerAndStatus(String managerId, LeaveStatusCompoff status) {
        return leaveCompoffRepo.findByManagerIdAndStatus(managerId, status);
    }

    @Override
    public List<LeaveCompoff> getPendingCompoffsForManager(String managerId) {
        return leaveCompoffRepo.findByManagerIdAndStatusOrderByWorkedDateDesc(managerId, LeaveStatusCompoff.PENDING);
    }
}