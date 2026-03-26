package com.paves.employee_leave_management.entities;

import lombok.*;

import java.util.List;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmployeeLeaveBalanceDTO {
    private String employeeId;
    private String employeeName;
    private String gender;
    private int year;
    private List<LeaveDetail> leaves;
}
