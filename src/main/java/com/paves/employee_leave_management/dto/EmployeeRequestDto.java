package com.paves.employee_leave_management.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeRequestDto {
    private String firstName;
    private String lastName;
    private String email;
    private String jobTitle;
    private BigDecimal salary;
    private LocalDate hireDate;
}
