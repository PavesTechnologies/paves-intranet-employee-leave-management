package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBlock;
import com.paves.employee_leave_management.enums.BlockStatus;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveBlockRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveBlockScheduler {

    private final LeaveBlockRepo leaveBlockRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void processLeaveBlock() {
        LocalDate today = LocalDate.now();

        // 1️⃣ Activate pending blocks whose start date has arrived
        List<LeaveBlock> toActivate = leaveBlockRepo.findByStatusAndStartDateLessThanEqual(BlockStatus.PENDING, today);
        for (LeaveBlock leaveBlock : toActivate) {
            leaveBlock.setStatus(BlockStatus.ACTIVE);
            leaveBlockRepo.save(leaveBlock);

            List<LeaveBalance> balancesToUpdate = new ArrayList<>();
            leaveBlock.getMembers().forEach(member -> {
                leaveBlock.getLeaveTypes().forEach(type -> {
                    LeaveBalance balance = leaveBalanceRepo
                            .getByEmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                                    member.getEmployeeId(),
                                    type.getLeaveTypeId(),
                                    today.getYear()
                            );
                    if (balance != null) {
                        balance.setBlockId(leaveBlock.getId());
                        balance.setIsBlocked(true);
                        balancesToUpdate.add(balance);
                    }
                });
            });
            if (!balancesToUpdate.isEmpty()) leaveBalanceRepo.saveAll(balancesToUpdate);
        }

        // 2️⃣ Expire active blocks whose end date has passed
        List<LeaveBlock> toExpire = leaveBlockRepo.findByStatusAndEndDateBefore(BlockStatus.ACTIVE, today);
        for (LeaveBlock leaveBlock : toExpire) {
            leaveBlock.setStatus(BlockStatus.INACTIVE);
            leaveBlockRepo.save(leaveBlock);

            List<LeaveBalance> balances = leaveBalanceRepo.findByBlockId(leaveBlock.getId());
            balances.forEach(balance -> {
                balance.setBlockId(null);
                balance.setIsBlocked(false);
            });
            if (!balances.isEmpty()) leaveBalanceRepo.saveAll(balances);
        }
    }
}
