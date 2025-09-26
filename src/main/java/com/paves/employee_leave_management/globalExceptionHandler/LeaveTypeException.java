package com.paves.employee_leave_management.globalExceptionHandler;


import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveTypeException extends RuntimeException{
    private String exMsg;
}
