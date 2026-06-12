package com.paves.employee_leave_management.utils;

import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.service.LeaveRequestService;
import com.paves.employee_leave_management.service.LeaveTypeServiceImple;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class CacheWarmup {

    private final LeaveTypeServiceImple leaveTypeServiceImple;

    public CacheWarmup(LeaveTypeServiceImple leaveTypeServiceImple) {
        this.leaveTypeServiceImple = leaveTypeServiceImple;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadCache() {
        try {
            log.info("Starting cache warmup...");
            leaveTypeServiceImple.getAllLeaveTypes();
            log.info("Cache warmup completed successfully");
        } catch (Exception e) {
            log.warn("Cache warmup skipped — Redis unavailable. " +
                    "App will continue normally. Error: {}", e.getMessage());
        }
    }
}
