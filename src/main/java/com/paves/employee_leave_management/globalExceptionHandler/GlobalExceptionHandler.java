package com.paves.employee_leave_management.globalExceptionHandler;


import com.paves.employee_leave_management.dto.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(EmployeeExceptionHandler.class)
    public ResponseEntity<String> handleException(EmployeeExceptionHandler employeeExceptionHandler) {
        return ResponseEntity.status(400).body(employeeExceptionHandler.getExMSg());
    }

    @ExceptionHandler(LeaveBalanceExceptionHandler.class)
    public ResponseEntity<String> handleException(LeaveBalanceExceptionHandler leaveBalanceExceptionHandler) {
        return ResponseEntity.status(400).body(leaveBalanceExceptionHandler.getExMsg());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, "Duplicate entry: leave type already exists", null));
    }

    @ExceptionHandler(HolidayExceptionHandler.class)
    public ResponseEntity<String> handleException(HolidayExceptionHandler holidayExceptionHandler) {
        return ResponseEntity.status(400).body(holidayExceptionHandler.getExMsg());
    }
}
