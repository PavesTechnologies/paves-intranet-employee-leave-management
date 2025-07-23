package com.paves.employee_leave_management.globalExceptionHandler;

import lombok.*;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class EmployeeExceptionHandler extends RuntimeException {
    private String exMSg;
}
