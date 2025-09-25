package com.paves.employee_leave_management.listener;

import com.paves.employee_leave_management.event.LeaveBalanceCreationEvent;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LeaveBalanceCreationEventListener {

    private final LeaveBalanceServiceInterface leaveBalanceService;

    public LeaveBalanceCreationEventListener(LeaveBalanceServiceInterface leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @TransactionalEventListener
    public void handleLeaveBalanceCreationEvent(LeaveBalanceCreationEvent event) {
        leaveBalanceService.createLeaveBalanceForAllEmployeesAsync(event.getLeaveType(), event.getJobId());
    }
}
