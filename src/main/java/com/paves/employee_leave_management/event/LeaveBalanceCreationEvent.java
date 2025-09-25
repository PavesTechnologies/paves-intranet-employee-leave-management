package com.paves.employee_leave_management.event;

import com.paves.employee_leave_management.entities.LeaveType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class LeaveBalanceCreationEvent extends ApplicationEvent {
    private final LeaveType leaveType;
    private final String jobId;

    public LeaveBalanceCreationEvent(Object source, LeaveType leaveType, String jobId) {
        super(source);
        this.leaveType = leaveType;
        this.jobId = jobId;
    }
}
