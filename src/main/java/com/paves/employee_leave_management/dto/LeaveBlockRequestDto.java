package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.BlockStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class LeaveBlockRequestDto {
    private String projectId;
    private List<String> members;
    private List<String> leaveTypeIds;
    private LocalDate startDate;
    private LocalDate endDate;
    private String managerId;
    private String reason;
    private BlockStatus status;
    private Integer year;
}
