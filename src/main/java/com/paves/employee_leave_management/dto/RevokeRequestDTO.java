package com.paves.employee_leave_management.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RevokeRequestDTO {
    private String employeeId;
    private int year;
    private String leaveId;
}
