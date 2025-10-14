package com.paves.employee_leave_management.globalExceptionHandler;


import com.paves.employee_leave_management.dto.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationExceptions(MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(obj -> obj.getDefaultMessage())
                .collect(Collectors.toList());

        // Optionally log errors for debugging
        errors.forEach(System.out::println);

        // Send only message, keep data = null
        ApiResponse<String> response = new ApiResponse<>(false,
                "Compoff cannot be applied beyond 28 days in the past.",
                null);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }


    // Handle generic runtime exceptions
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(LeaveTypeException.class)
    public ResponseEntity<String> handleException(LeaveTypeException leaveTypeException) {
        return ResponseEntity.status(400).body(leaveTypeException.getExMsg());
    }

}
