package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.service.JobProgressManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@CrossOrigin
@RequestMapping("/api/leave-types/jobs")
public class JobProgressController {

    private final JobProgressManager jobProgressManager;

    public JobProgressController(JobProgressManager jobProgressManager) {
        this.jobProgressManager = jobProgressManager;
    }

    @GetMapping("/{jobId}/stream")
    public SseEmitter streamJobProgress(@PathVariable String jobId) {
        return jobProgressManager.register(jobId);
    }
}
