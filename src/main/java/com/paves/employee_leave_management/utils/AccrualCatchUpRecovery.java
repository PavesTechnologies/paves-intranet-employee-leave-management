package com.paves.employee_leave_management.utils;

import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs the same catch-up-aware accrual check on startup that the nightly cron runs — so a
// server that was down over a scheduled accrual (the cron doesn't queue/replay a missed
// firing) catches up immediately on restart instead of waiting for the next midnight.
// Safe to call unconditionally: triggerMonthlyLeaveAccrual() is idempotent — it only credits
// whatever monthly cycles have actually elapsed since each balance's lastAccrualDate, so a
// no-op run (nothing due) costs nothing.
@Component
@Slf4j
public class AccrualCatchUpRecovery {

    private final LeaveBalanceServiceInterface leaveBalanceService;

    public AccrualCatchUpRecovery(LeaveBalanceServiceInterface leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAccrualOnStartup() {
        try {
            log.info("Running accrual catch-up check on startup...");
            leaveBalanceService.triggerMonthlyLeaveAccrual();
        } catch (Exception e) {
            log.warn("Accrual catch-up check on startup failed: {}", e.getMessage());
        }
    }
}
