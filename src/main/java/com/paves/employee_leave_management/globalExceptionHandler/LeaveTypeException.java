package com.paves.employee_leave_management.globalExceptionHandler;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveTypeException extends RuntimeException {
    private String exMsg;
}
