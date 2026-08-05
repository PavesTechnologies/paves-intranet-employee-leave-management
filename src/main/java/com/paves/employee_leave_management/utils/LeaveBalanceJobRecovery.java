package com.paves.employee_leave_management.utils;

import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceJobServiceInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

// Resumes leave-balance jobs left stuck in PENDING/RUNNING by a server restart that happened
// mid-job — the leaveBalanceExecutor thread pool is in-memory, so those tasks are lost when the
// JVM stops. See LeaveBalanceJobServiceImplementation.claimStuckJobs() for the periodic
// (no-restart-required) counterpart.
@Component
@Slf4j
public class LeaveBalanceJobRecovery {

    private final LeaveBalanceJobServiceInterface leaveBalanceJobService;

    public LeaveBalanceJobRecovery(LeaveBalanceJobServiceInterface leaveBalanceJobService) {
        this.leaveBalanceJobService = leaveBalanceJobService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeStuckJobsOnStartup() {
        try {
            List<LeaveBalanceJob> resumed = leaveBalanceJobService.claimStuckJobs();
            if (resumed.isEmpty()) {
                return;
            }
            log.warn("Resuming {} leave balance job(s) stuck from before this restart", resumed.size());
            resumed.forEach(job ->
                    leaveBalanceJobService.processLeaveBalancesAsync(job.getJobId(), job.getLeaveTypeId()));
        } catch (Exception e) {
            log.warn("Failed to resume stuck leave balance jobs on startup: {}", e.getMessage());
        }
    }
}
