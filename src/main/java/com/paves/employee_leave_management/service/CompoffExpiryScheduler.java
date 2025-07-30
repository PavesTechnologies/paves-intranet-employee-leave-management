package com.paves.employee_leave_management.service;


import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveStatusCompoff;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompoffExpiryScheduler {
    private final LeaveCompoffRepo leaveCompoffRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private  final LeaveBalanceServiceInterface balanceServiceInterface;

    @Scheduled(cron = "0 0 2 * * ?") // Runs every day at 2 AM
    public void expireUnusedCompoffs() {
        List<LeaveCompoff> compoffs = leaveCompoffRepo.findByStatus(LeaveStatusCompoff.APPROVED);

        for (LeaveCompoff compoff : compoffs) {
            if (compoff.getExpiryDate() != null &&
                    LocalDate.now().isAfter(compoff.getExpiryDate())) {

                compoff.setStatus(LeaveStatusCompoff.EXPIRED);
                leaveCompoffRepo.save(compoff);

                LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(compoff.getEmployeeId(), "L-COMPOFF",LocalDate.now().getYear());

                if (balance!=null) {
//                    balanceServiceInterface.upda
                }
            }
        }
    }

}
