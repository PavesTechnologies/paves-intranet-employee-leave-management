package com.paves.employee_leave_management.entities;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveBalanceUpdateRequest {
    private String employeeId;
    private List<BalanceUpdate> balances;
    private String performedBy;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class BalanceUpdate {
        private String leaveTypeId;
        private Double remainingLeaves;
        private Integer Year;
    }
}
