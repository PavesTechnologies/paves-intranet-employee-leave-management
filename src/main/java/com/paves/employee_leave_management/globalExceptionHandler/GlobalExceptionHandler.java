package com.paves.employee_leave_management.globalExceptionHandler;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {


//    @ExceptionHandler(EmployeeExceptionHandler.class)
//    public ResponseEntity<String> handleException(EmployeeExceptionHandler employeeExceptionHandler) {
//        return ResponseEntity.status(400).body(employeeExceptionHandler.getExMSg());
//    }

    @ExceptionHandler(LeaveBalanceExceptionHandler.class)
    public ResponseEntity<String> handleException(LeaveBalanceExceptionHandler leaveBalanceExceptionHandler) {
        return ResponseEntity.status(400).body(leaveBalanceExceptionHandler.getExMsg());
    }
}
