package com.paves.employee_leave_management.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TodayOnLeaveEmpDetails {
    private String employeeName;
    private String session;
}
