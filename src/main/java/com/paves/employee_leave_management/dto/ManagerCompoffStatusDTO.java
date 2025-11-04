package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.JobStatus;
import lombok.Data;

@Data
public class ManagerCompoffStatusDTO {
    private String managerId;
    private JobStatus.LeaveStatusCompoff status;
}
