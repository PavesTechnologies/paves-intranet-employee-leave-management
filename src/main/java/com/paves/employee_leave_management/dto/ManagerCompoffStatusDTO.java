package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.LeaveStatusCompoff;
import lombok.Data;

@Data
public class ManagerCompoffStatusDTO {
    private String managerId;
    private LeaveStatusCompoff status;
}
