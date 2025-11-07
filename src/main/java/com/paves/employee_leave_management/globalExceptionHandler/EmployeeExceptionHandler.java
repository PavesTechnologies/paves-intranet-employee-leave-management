package com.paves.employee_leave_management.globalExceptionHandler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class EmployeeExceptionHandler extends RuntimeException {
    private String exMSg;
}
