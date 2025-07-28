package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.EmailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Higher-level email notification service with business logic wrapper.
 * Provides convenient async methods with additional validation, logging, and company-specific formatting.
 * 
 * @author Email Service
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {
    
    private final OutlookEmailService outlookEmailService;
    
    private static final String COMPANY_SIGNATURE = "\n\n---\nBest regards,\nPAVES Employee Leave Management System\nThis is an automated message. Please do not reply to this email.";
    private static final DateTimeFormatter AUDIT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Sends a basic notification email asynchronously
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param message Email message content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendNotificationAsync(String to, String subject, String message) {
        log.info("Sending notification email to: {} with subject: {}", to, subject);
        
        // Add audit trail
        String auditMessage = addAuditTrail(message, "NOTIFICATION");
        
        // Add company signature
        String finalMessage = auditMessage + COMPANY_SIGNATURE;
        
        return outlookEmailService.sendEmailAsync(to, subject, finalMessage)
                .thenApply(result -> {
                    logEmailResult(result, "NOTIFICATION", to);
                    return result;
                });
    }
    
    /**
     * Sends bulk notification emails asynchronously to multiple recipients
     * 
     * @param recipients List of email recipient addresses
     * @param subject Email subject
     * @param message Email message content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendBulkNotificationAsync(List<String> recipients, String subject, String message) {
        log.info("Sending bulk notification email to {} recipients with subject: {}", recipients.size(), subject);
        
        // Validate recipients list
        if (recipients == null || recipients.isEmpty()) {
            log.warn("Attempted to send bulk notification with empty recipients list");
            return CompletableFuture.completedFuture(
                EmailResult.failure("No recipients provided for bulk notification", "NO_RECIPIENTS", "None", subject)
            );
        }
        
        // Filter out invalid emails and log them
        List<String> validEmails = recipients.stream()
                .filter(email -> {
                    boolean isValid = outlookEmailService.isValidEmail(email);
                    if (!isValid) {
                        log.warn("Invalid email address filtered out from bulk notification: {}", email);
                    }
                    return isValid;
                })
                .collect(Collectors.toList());
        
        if (validEmails.isEmpty()) {
            log.error("No valid email addresses found in bulk notification recipients");
            return CompletableFuture.completedFuture(
                EmailResult.failure("No valid email addresses provided", "NO_VALID_RECIPIENTS", 
                                  String.join(", ", recipients), subject)
            );
        }
        
        // Add audit trail
        String auditMessage = addAuditTrail(message, "BULK_NOTIFICATION");
        
        // Add company signature
        String finalMessage = auditMessage + COMPANY_SIGNATURE;
        
        return outlookEmailService.sendEmailToMultipleAsync(validEmails, subject, finalMessage)
                .thenApply(result -> {
                    logEmailResult(result, "BULK_NOTIFICATION", String.join(", ", validEmails));
                    return result;
                });
    }
    
    /**
     * Sends an HTML notification email asynchronously
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param htmlContent HTML email content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendHtmlNotificationAsync(String to, String subject, String htmlContent) {
        log.info("Sending HTML notification email to: {} with subject: {}", to, subject);
        
        // Add HTML audit trail
        String auditHtml = addHtmlAuditTrail(htmlContent, "HTML_NOTIFICATION");
        
        // Add HTML company signature
        String finalHtmlContent = auditHtml + getHtmlSignature();
        
        return outlookEmailService.sendEmailAsync(to, subject, finalHtmlContent, true)
                .thenApply(result -> {
                    logEmailResult(result, "HTML_NOTIFICATION", to);
                    return result;
                });
    }
    
    /**
     * Sends an email with attachments asynchronously
     * Note: This is a placeholder implementation. Full attachment support would require
     * additional MimeMessageHelper configuration in OutlookEmailService.
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param body Email body content
     * @param attachments List of files to attach
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailWithAttachmentsAsync(String to, String subject, String body, List<File> attachments) {
        log.info("Sending email with attachments to: {} with subject: {} (attachments: {})", 
                to, subject, attachments != null ? attachments.size() : 0);
        
        // For now, send without attachments and log the limitation
        log.warn("Attachment support is not fully implemented. Sending email without attachments.");
        
        // Add audit trail mentioning attempted attachments
        String auditMessage = addAuditTrail(body, "EMAIL_WITH_ATTACHMENTS");
        if (attachments != null && !attachments.isEmpty()) {
            auditMessage += "\n\nNote: " + attachments.size() + " attachment(s) were intended for this email.";
        }
        
        String finalMessage = auditMessage + COMPANY_SIGNATURE;
        
        return outlookEmailService.sendEmailAsync(to, subject, finalMessage)
                .thenApply(result -> {
                    logEmailResult(result, "EMAIL_WITH_ATTACHMENTS", to);
                    return result;
                });
    }
    
    /**
     * Sends an email asynchronously (basic wrapper method)
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param body Email body content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailAsync(String to, String subject, String body) {
        log.debug("Sending basic email to: {} with subject: {}", to, subject);
        
        // Add minimal audit trail
        String auditMessage = addAuditTrail(body, "EMAIL");
        String finalMessage = auditMessage + COMPANY_SIGNATURE;
        
        return outlookEmailService.sendEmailAsync(to, subject, finalMessage)
                .thenApply(result -> {
                    logEmailResult(result, "EMAIL", to);
                    return result;
                });
    }
    
    /**
     * Sends an HTML email asynchronously (basic wrapper method)
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param htmlBody HTML email body content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendHtmlEmailAsync(String to, String subject, String htmlBody) {
        log.debug("Sending HTML email to: {} with subject: {}", to, subject);
        
        // Add HTML audit trail
        String auditHtml = addHtmlAuditTrail(htmlBody, "HTML_EMAIL");
        String finalHtmlContent = auditHtml + getHtmlSignature();
        
        return outlookEmailService.sendEmailAsync(to, subject, finalHtmlContent, true)
                .thenApply(result -> {
                    logEmailResult(result, "HTML_EMAIL", to);
                    return result;
                });
    }
    
    /**
     * Sends an email to multiple recipients asynchronously
     * 
     * @param recipients List of email recipient addresses
     * @param subject Email subject
     * @param body Email body content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailToMultipleAsync(List<String> recipients, String subject, String body) {
        log.debug("Sending email to {} recipients with subject: {}", recipients != null ? recipients.size() : 0, subject);
        
        // Add audit trail
        String auditMessage = addAuditTrail(body, "MULTIPLE_EMAIL");
        String finalMessage = auditMessage + COMPANY_SIGNATURE;
        
        return outlookEmailService.sendEmailToMultipleAsync(recipients, subject, finalMessage)
                .thenApply(result -> {
                    logEmailResult(result, "MULTIPLE_EMAIL", String.join(", ", recipients != null ? recipients : List.of()));
                    return result;
                });
    }
    
    /**
     * Sends an email with CC and BCC recipients asynchronously
     * 
     * @param to Primary recipient email address
     * @param cc List of CC recipient addresses
     * @param bcc List of BCC recipient addresses
     * @param subject Email subject
     * @param body Email body content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailWithCcBccAsync(String to, List<String> cc, List<String> bcc, String subject, String body) {
        log.debug("Sending email with CC/BCC to: {} with subject: {}", to, subject);
        
        // Add audit trail
        String auditMessage = addAuditTrail(body, "EMAIL_WITH_CC_BCC");
        String finalMessage = auditMessage + COMPANY_SIGNATURE;
        
        return outlookEmailService.sendEmailWithCcBccAsync(to, cc, bcc, subject, finalMessage)
                .thenApply(result -> {
                    logEmailResult(result, "EMAIL_WITH_CC_BCC", to);
                    return result;
                });
    }
    
    /**
     * Tests the email connection asynchronously
     * 
     * @return CompletableFuture containing EmailResult with connection test results
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> testEmailConnectionAsync() {
        log.info("Testing email connection through notification service");
        
        return outlookEmailService.testConnectionAsync()
                .thenApply(result -> {
                    logEmailResult(result, "CONNECTION_TEST", "N/A");
                    return result;
                });
    }
    
    /**
     * Validates an email address
     * Note: This is a synchronous method as it's just validation
     * 
     * @param email Email address to validate
     * @return true if email is valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        return outlookEmailService.isValidEmail(email);
    }
    
    /**
     * Sends a leave request notification email
     * 
     * @param to Recipient email address
     * @param employeeName Name of the employee
     * @param leaveType Type of leave requested
     * @param startDate Leave start date
     * @param endDate Leave end date
     * @param reason Leave reason
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendLeaveRequestNotificationAsync(String to, String employeeName, 
                                                                           String leaveType, String startDate, 
                                                                           String endDate, String reason) {
        log.info("Sending leave request notification to: {} for employee: {}", to, employeeName);
        
        String subject = String.format("Leave Request Submitted - %s (%s)", employeeName, leaveType);
        
        String message = String.format(
            "Dear Manager,\n\n" +
            "A new leave request has been submitted by %s.\n\n" +
            "Leave Details:\n" +
            "- Employee: %s\n" +
            "- Leave Type: %s\n" +
            "- Start Date: %s\n" +
            "- End Date: %s\n" +
            "- Reason: %s\n\n" +
            "Please review and approve/reject this request in the employee leave management system.\n",
            employeeName, employeeName, leaveType, startDate, endDate, reason
        );
        
        return sendNotificationAsync(to, subject, message);
    }
    
    /**
     * Sends a leave approval notification email
     * 
     * @param to Recipient email address
     * @param employeeName Name of the employee
     * @param leaveType Type of leave
     * @param startDate Leave start date
     * @param endDate Leave end date
     * @param approverName Name of the approver
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendLeaveApprovalNotificationAsync(String to, String employeeName, 
                                                                            String leaveType, String startDate, 
                                                                            String endDate, String approverName) {
        log.info("Sending leave approval notification to: {} for employee: {}", to, employeeName);
        
        String subject = String.format("Leave Request Approved - %s (%s)", employeeName, leaveType);
        
        String message = String.format(
            "Dear %s,\n\n" +
            "Your leave request has been approved.\n\n" +
            "Leave Details:\n" +
            "- Leave Type: %s\n" +
            "- Start Date: %s\n" +
            "- End Date: %s\n" +
            "- Approved By: %s\n\n" +
            "Enjoy your time off!\n",
            employeeName, leaveType, startDate, endDate, approverName
        );
        
        return sendNotificationAsync(to, subject, message);
    }
    
    /**
     * Sends a leave rejection notification email
     * 
     * @param to Recipient email address
     * @param employeeName Name of the employee
     * @param leaveType Type of leave
     * @param startDate Leave start date
     * @param endDate Leave end date
     * @param rejectionReason Reason for rejection
     * @param approverName Name of the approver
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendLeaveRejectionNotificationAsync(String to, String employeeName, 
                                                                             String leaveType, String startDate, 
                                                                             String endDate, String rejectionReason, 
                                                                             String approverName) {
        log.info("Sending leave rejection notification to: {} for employee: {}", to, employeeName);
        
        String subject = String.format("Leave Request Rejected - %s (%s)", employeeName, leaveType);
        
        String message = String.format(
            "Dear %s,\n\n" +
            "Unfortunately, your leave request has been rejected.\n\n" +
            "Leave Details:\n" +
            "- Leave Type: %s\n" +
            "- Start Date: %s\n" +
            "- End Date: %s\n" +
            "- Rejection Reason: %s\n" +
            "- Reviewed By: %s\n\n" +
            "Please contact your manager if you have any questions.\n",
            employeeName, leaveType, startDate, endDate, rejectionReason, approverName
        );
        
        return sendNotificationAsync(to, subject, message);
    }
    
    /**
     * Adds audit trail information to email content
     */
    private String addAuditTrail(String originalMessage, String emailType) {
        String timestamp = LocalDateTime.now().format(AUDIT_DATE_FORMAT);
        return String.format("[%s] %s\n\n%s", emailType, timestamp, originalMessage);
    }
    
    /**
     * Adds HTML audit trail information to email content
     */
    private String addHtmlAuditTrail(String originalHtmlContent, String emailType) {
        String timestamp = LocalDateTime.now().format(AUDIT_DATE_FORMAT);
        return String.format(
            "<div style='font-size: 10px; color: #666; margin-bottom: 10px;'>[%s] %s</div>\n%s", 
            emailType, timestamp, originalHtmlContent
        );
    }
    
    /**
     * Gets HTML signature for emails
     */
    private String getHtmlSignature() {
        return "<hr style='border: 1px solid #ccc; margin: 20px 0;'>" +
               "<p style='font-size: 12px; color: #666;'>" +
               "<strong>Best regards,</strong><br>" +
               "PAVES Employee Leave Management System<br>" +
               "<em>This is an automated message. Please do not reply to this email.</em>" +
               "</p>";
    }
    
    /**
     * Logs email operation results for audit purposes
     */
    private void logEmailResult(EmailResult result, String emailType, String recipients) {
        if (result.isSuccess()) {
            log.info("Email operation successful - Type: {}, Recipients: {}, Message: {}", 
                    emailType, recipients, result.getMessage());
        } else {
            log.error("Email operation failed - Type: {}, Recipients: {}, Error: {} (Code: {})", 
                    emailType, recipients, result.getMessage(), result.getErrorCode());
        }
    }
}
