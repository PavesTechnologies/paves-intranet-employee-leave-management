package com.paves.employee_leave_management.entities;


import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LeaveDetail {
    private String leaveTypeId;
    private String leaveTypeName;
    private double remainingLeaves;
}
