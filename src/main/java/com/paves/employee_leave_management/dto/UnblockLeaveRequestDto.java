package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;


@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UnblockLeaveRequestDto {
    private String blockId;
    private List<String> employeeIds;
    private List<String> leaveTypeIds;
    private Integer year;
}
