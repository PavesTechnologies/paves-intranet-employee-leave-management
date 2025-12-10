package com.paves.employee_leave_management.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}