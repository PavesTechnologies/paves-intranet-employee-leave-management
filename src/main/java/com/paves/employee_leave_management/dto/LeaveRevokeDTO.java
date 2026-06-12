package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.LeaveRevokeStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LeaveRevokeDTO {
    private String revokeId;
    private String leaveRequestId;
    private String leaveName;
    private String employeeId;
    private String employeeName;
    private Double days;
    private LocalDate startDate;
    private LocalDate endDate;
    private LeaveRevokeStatus status;
    private String reason;
    private String managerId;
    private Integer year;
}
