package com.paves.employee_leave_management.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * Data Transfer Object representing the result of an email operation.
 * Contains success/failure information, error details, and timestamp.
 * 
 * @author Email Service
 * @version 1.0
 */
@Data
@Builder
@Slf4j
public class EmailResult {
    
    /**
     * Indicates whether the email operation was successful
     */
    private final boolean success;
    
    /**
     * Human-readable message describing the result (success or error details)
     */
    private final String message;
    
    /**
     * Error code for programmatic handling of failures
     */
    private final String errorCode;
    
    /**
     * Timestamp when the email operation completed
     */
    @Builder.Default
    private final Instant timestamp = Instant.now();
    
    /**
     * Exception details if the operation failed
     */
    private final Exception exception;
    
    /**
     * Email address(es) that were targeted by the operation
     */
    private final String recipients;
    
    /**
     * Subject of the email that was sent or attempted to be sent
     */
    private final String subject;
    
    /**
     * Creates a successful EmailResult
     * 
     * @param message Success message
     * @param recipients Email recipients
     * @param subject Email subject
     * @return EmailResult indicating success
     */
    public static EmailResult success(String message, String recipients, String subject) {
        log.debug("Email operation successful: {} for recipients: {}", message, recipients);
        return EmailResult.builder()
                .success(true)
                .message(message)
                .recipients(recipients)
                .subject(subject)
                .build();
    }
    
    /**
     * Creates a failed EmailResult with error details
     * 
     * @param message Error message
     * @param errorCode Error code for programmatic handling
     * @param exception Exception that caused the failure
     * @param recipients Email recipients
     * @param subject Email subject
     * @return EmailResult indicating failure
     */
    public static EmailResult failure(String message, String errorCode, Exception exception, 
                                    String recipients, String subject) {
        log.error("Email operation failed: {} (Code: {}) for recipients: {}", message, errorCode, recipients, exception);
        return EmailResult.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .exception(exception)
                .recipients(recipients)
                .subject(subject)
                .build();
    }
    
    /**
     * Creates a failed EmailResult with error details (no exception)
     * 
     * @param message Error message
     * @param errorCode Error code for programmatic handling
     * @param recipients Email recipients
     * @param subject Email subject
     * @return EmailResult indicating failure
     */
    public static EmailResult failure(String message, String errorCode, String recipients, String subject) {
        log.error("Email operation failed: {} (Code: {}) for recipients: {}", message, errorCode, recipients);
        return EmailResult.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .recipients(recipients)
                .subject(subject)
                .build();
    }
    
    /**
     * Creates a connection test result
     * 
     * @param success Whether connection test was successful
     * @param message Result message
     * @return EmailResult for connection test
     */
    public static EmailResult connectionTest(boolean success, String message) {
        log.info("Email connection test result: {} - {}", success ? "SUCCESS" : "FAILURE", message);
        return EmailResult.builder()
                .success(success)
                .message(message)
                .recipients("N/A - Connection Test")
                .subject("N/A - Connection Test")
                .errorCode(success ? null : "CONNECTION_FAILED")
                .build();
    }
}
