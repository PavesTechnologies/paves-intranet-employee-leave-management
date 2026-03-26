package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AllPeopleLeaveBalance implements Serializable {
    private String employeeId;
    private String employeeName;
    private String leaveTypeId;
    private String leaveTypeName;
    private String leaveBalance;
    private double remainingLeaves;
    private Integer year;
    private String gender;

}
