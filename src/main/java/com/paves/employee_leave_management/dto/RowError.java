package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RowError {
    private int rowNumber;
    private String message;
}
