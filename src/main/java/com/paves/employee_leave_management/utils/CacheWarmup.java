package com.paves.employee_leave_management.utils;

import com.paves.employee_leave_management.service.LeaveRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Component
public class CacheWarmup {

    @Autowired
    private LeaveRequestService leaveRequestService;

    @EventListener(ApplicationReadyEvent.class)
    public void loadCache() {
        leaveRequestService.getLeaveRequestsByEmployee("testUser");
    }
}
