package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UnblockLeaveRequestDto {

    private String blockId;
    private List<EmployeeUnblockRequest> unblockRequests;
    private Integer year;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeUnblockRequest {
        private String employeeId;
        private List<String> leaveTypeIds;
    }
}
