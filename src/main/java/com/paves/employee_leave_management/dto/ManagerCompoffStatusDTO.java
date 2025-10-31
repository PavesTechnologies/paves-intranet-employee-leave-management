package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.ApproverType;
import lombok.Data;

@Data
public class ManagerCompoffStatusDTO {
    private String managerId;
    private ApproverType.LeaveStatusCompoff status;
}
