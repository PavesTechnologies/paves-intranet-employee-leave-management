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

    // -----------------------
    // EMPLOYEE EXCEPTIONS
    // -----------------------
    @ExceptionHandler(EmployeeExceptionHandler.class)
    public ResponseEntity<ApiResponse<String>> handleEmployeeException(EmployeeExceptionHandler ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, ex.getExMSg(), null));
    }

    // -----------------------
    // LEAVE BALANCE EXCEPTIONS
    // -----------------------
    @ExceptionHandler(LeaveBalanceExceptionHandler.class)
    public ResponseEntity<ApiResponse<String>> handleLeaveBalanceException(LeaveBalanceExceptionHandler ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, ex.getExMsg(), null));
    }

    // -----------------------
    // LEAVE TYPE EXCEPTIONS
    // -----------------------
    @ExceptionHandler(LeaveTypeException.class)
    public ResponseEntity<ApiResponse<String>> handleLeaveTypeException(LeaveTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, ex.getExMsg(), null));
    }

    // -----------------------
    // HOLIDAY EXCEPTIONS
    // -----------------------
    @ExceptionHandler(HolidayExceptionHandler.class)
    public ResponseEntity<ApiResponse<String>> handleHolidayException(HolidayExceptionHandler ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, ex.getExMsg(), null));
    }

    // -----------------------
    // LEAVE BLOCK EXCEPTIONS
    // -----------------------
    @ExceptionHandler(LeaveBlockException.class)
    public ResponseEntity<ApiResponse<String>> handleLeaveBlockException(LeaveBlockException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, ex.getExMsg(), null));
    }

    // -----------------------
    // DATABASE CONSTRAINT EXCEPTIONS
    // -----------------------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, "Database constraint violation: " + ex.getMostSpecificCause().getMessage(), null));
    }

    // -----------------------
    // VALIDATION ERRORS
    // -----------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationExceptions(MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(obj -> obj.getDefaultMessage())
                .collect(Collectors.toList());

        // Optionally log errors for debugging
        errors.forEach(System.out::println);

        // Join all error messages with a comma or return the first error
        String errorMessage = errors.isEmpty() ? "Validation failed" : String.join(", ", errors);

        // Send the actual validation error message
        ApiResponse<String> response = new ApiResponse<>(false, errorMessage, null);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // -----------------------
    // RUNTIME / FALLBACK
    // -----------------------

    // Handle generic runtime exceptions
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException ex) {
        ex.printStackTrace(); // useful for debugging logs
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
