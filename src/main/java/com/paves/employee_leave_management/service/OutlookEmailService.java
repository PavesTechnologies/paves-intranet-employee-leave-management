
package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.config.EmailProperties;
import com.paves.employee_leave_management.dto.EmailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
//import jax.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core async email service for Outlook SMTP operations.
 * Provides thread-safe email sending capabilities with comprehensive error handling.
 * 
 * @author Email Service
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutlookEmailService implements AutoCloseable {
    
    private final JavaMailSender javaMailSender;
    private final EmailProperties emailProperties;
    
    // Compiled regex pattern for email validation (thread-safe)
    private final Pattern emailPattern = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    
    /**
     * Sends an email asynchronously to a single recipient
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param body Email body (plain text)
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailAsync(String to, String subject, String body) {
        return sendEmailAsync(to, subject, body, false);
    }
    
    /**
     * Sends an email asynchronously to a single recipient with HTML support
     * 
     * @param to Email recipient address
     * @param subject Email subject
     * @param body Email body
     * @param isHtml Whether the body contains HTML content
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailAsync(String to, String subject, String body, boolean isHtml) {
        log.debug("Initiating async email send to: {} with subject: {}", to, subject);
        
        try {
            // Input validation
            if (!isValidInput(to, subject, body)) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Invalid input parameters", "INVALID_INPUT", to, subject)
                );
            }
            
            // Email validation
            if (!isValidEmail(to)) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Invalid email address: " + to, "INVALID_EMAIL", to, subject)
                );
            }
            
            // Create and send email
            MimeMessage message = createMimeMessage(to, null, null, subject, body, isHtml);
            javaMailSender.send(message);
            
            log.info("Email sent successfully to: {} with subject: {}", to, subject);
            return CompletableFuture.completedFuture(
                EmailResult.success("Email sent successfully", to, subject)
            );
            
        } catch (MessagingException e) {
            log.error("MessagingException while sending email to: {}", to, e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Failed to create email message: " + e.getMessage(), 
                                  "MESSAGING_ERROR", e, to, subject)
            );
        } catch (MailException e) {
            log.error("MailException while sending email to: {}", to, e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Failed to send email: " + e.getMessage(), 
                                  "MAIL_SEND_ERROR", e, to, subject)
            );
        } catch (Exception e) {
            log.error("Unexpected error while sending email to: {}", to, e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Unexpected error: " + e.getMessage(), 
                                  "UNEXPECTED_ERROR", e, to, subject)
            );
        }
    }
    
    /**
     * Sends an email asynchronously to multiple recipients
     * 
     * @param recipients List of email recipient addresses
     * @param subject Email subject
     * @param body Email body (plain text)
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailToMultipleAsync(List<String> recipients, String subject, String body) {
        log.debug("Initiating async email send to {} recipients with subject: {}", recipients.size(), subject);
        
        try {
            // Input validation
            if (recipients == null || recipients.isEmpty()) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("No recipients provided", "NO_RECIPIENTS", "None", subject)
                );
            }
            
            if (!isValidInput("dummy@example.com", subject, body)) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Invalid subject or body", "INVALID_INPUT", 
                                      String.join(", ", recipients), subject)
                );
            }
            
            // Validate all email addresses
            List<String> invalidEmails = recipients.stream()
                    .filter(email -> !isValidEmail(email))
                    .collect(Collectors.toList());
            
            if (!invalidEmails.isEmpty()) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Invalid email addresses: " + String.join(", ", invalidEmails), 
                                      "INVALID_EMAIL", String.join(", ", recipients), subject)
                );
            }
            
            // Create and send email to multiple recipients
            MimeMessage message = createMimeMessage(recipients, null, null, subject, body, false);
            javaMailSender.send(message);
            
            String recipientList = String.join(", ", recipients);
            log.info("Email sent successfully to {} recipients: {}", recipients.size(), recipientList);
            return CompletableFuture.completedFuture(
                EmailResult.success("Email sent successfully to " + recipients.size() + " recipients", 
                                  recipientList, subject)
            );
            
        } catch (MessagingException e) {
            log.error("MessagingException while sending email to multiple recipients", e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Failed to create email message: " + e.getMessage(), 
                                  "MESSAGING_ERROR", e, String.join(", ", recipients), subject)
            );
        } catch (MailException e) {
            log.error("MailException while sending email to multiple recipients", e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Failed to send email: " + e.getMessage(), 
                                  "MAIL_SEND_ERROR", e, String.join(", ", recipients), subject)
            );
        } catch (Exception e) {
            log.error("Unexpected error while sending email to multiple recipients", e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Unexpected error: " + e.getMessage(), 
                                  "UNEXPECTED_ERROR", e, String.join(", ", recipients), subject)
            );
        }
    }
    
    /**
     * Sends an email asynchronously with CC and BCC recipients
     * 
     * @param to Primary recipient email address
     * @param cc List of CC recipient addresses (can be null)
     * @param bcc List of BCC recipient addresses (can be null)
     * @param subject Email subject
     * @param body Email body (plain text)
     * @return CompletableFuture containing EmailResult
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> sendEmailWithCcBccAsync(String to, List<String> cc, List<String> bcc, 
                                                                 String subject, String body) {
        log.debug("Initiating async email send with CC/BCC to: {} with subject: {}", to, subject);
        
        try {
            // Input validation
            if (!isValidInput(to, subject, body)) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Invalid input parameters", "INVALID_INPUT", to, subject)
                );
            }
            
            // Validate primary recipient
            if (!isValidEmail(to)) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Invalid primary email address: " + to, "INVALID_EMAIL", to, subject)
                );
            }
            
            // Validate CC recipients
            if (cc != null && !cc.isEmpty()) {
                List<String> invalidCcEmails = cc.stream()
                        .filter(email -> !isValidEmail(email))
                        .collect(Collectors.toList());
                
                if (!invalidCcEmails.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        EmailResult.failure("Invalid CC email addresses: " + String.join(", ", invalidCcEmails), 
                                          "INVALID_EMAIL", to, subject)
                    );
                }
            }
            
            // Validate BCC recipients
            if (bcc != null && !bcc.isEmpty()) {
                List<String> invalidBccEmails = bcc.stream()
                        .filter(email -> !isValidEmail(email))
                        .collect(Collectors.toList());
                
                if (!invalidBccEmails.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        EmailResult.failure("Invalid BCC email addresses: " + String.join(", ", invalidBccEmails), 
                                          "INVALID_EMAIL", to, subject)
                    );
                }
            }
            
            // Create and send email with CC/BCC
            MimeMessage message = createMimeMessage(to, cc, bcc, subject, body, false);
            javaMailSender.send(message);
            
            String allRecipients = buildRecipientString(to, cc, bcc);
            log.info("Email with CC/BCC sent successfully. Recipients: {}", allRecipients);
            return CompletableFuture.completedFuture(
                EmailResult.success("Email sent successfully with CC/BCC", allRecipients, subject)
            );
            
        } catch (MessagingException e) {
            log.error("MessagingException while sending email with CC/BCC to: {}", to, e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Failed to create email message: " + e.getMessage(), 
                                  "MESSAGING_ERROR", e, to, subject)
            );
        } catch (MailException e) {
            log.error("MailException while sending email with CC/BCC to: {}", to, e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Failed to send email: " + e.getMessage(), 
                                  "MAIL_SEND_ERROR", e, to, subject)
            );
        } catch (Exception e) {
            log.error("Unexpected error while sending email with CC/BCC to: {}", to, e);
            return CompletableFuture.completedFuture(
                EmailResult.failure("Unexpected error: " + e.getMessage(), 
                                  "UNEXPECTED_ERROR", e, to, subject)
            );
        }
    }
    
    /**
     * Tests the email connection asynchronously
     * 
     * @return CompletableFuture containing EmailResult with connection test results
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailResult> testConnectionAsync() {
        log.info("Testing email connection to Outlook SMTP server");
        
        try {
            // Create a test session
            Session session = Session.getInstance(emailProperties.createTestProperties());
            
            // Test connection
            Transport transport = session.getTransport("smtp");
            transport.connect(
                emailProperties.getHost(),
                emailProperties.getPort(),
                emailProperties.getUsername(),
                emailProperties.getPassword()
            );
            
            // Check if connected
            if (transport.isConnected()) {
                transport.close();
                log.info("Email connection test successful");
                return CompletableFuture.completedFuture(
                    EmailResult.connectionTest(true, "Connection to Outlook SMTP server successful")
                );
            } else {
                log.warn("Email connection test failed - not connected");
                return CompletableFuture.completedFuture(
                    EmailResult.connectionTest(false, "Failed to establish connection to SMTP server")
                );
            }
            
        } catch (MessagingException e) {
            log.error("Email connection test failed with MessagingException", e);
            return CompletableFuture.completedFuture(
                EmailResult.connectionTest(false, "Connection failed: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Email connection test failed with unexpected error", e);
            return CompletableFuture.completedFuture(
                EmailResult.connectionTest(false, "Unexpected connection error: " + e.getMessage())
            );
        }
    }
    
    /**
     * Validates an email address using regex pattern
     * 
     * @param email Email address to validate
     * @return true if email is valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        String trimmedEmail = email.trim();
        
        // Use strict validation if enabled
        if (emailProperties.isStrictEmailValidation()) {
            return emailPattern.matcher(trimmedEmail).matches();
        } else {
            // Basic validation - just check for @ and domain
            return trimmedEmail.contains("@") && trimmedEmail.contains(".") && trimmedEmail.length() > 5;
        }
    }
    
    /**
     * Creates a MimeMessage for single recipient
     */
    private MimeMessage createMimeMessage(String to, List<String> cc, List<String> bcc, 
                                        String subject, String body, boolean isHtml) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
//        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // Set from address
        try {
            helper.setFrom(emailProperties.getDefaultFromAddress(), emailProperties.getDefaultFromName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        // Set recipients
        helper.setTo(to);

        if (cc != null && !cc.isEmpty()) {
            helper.setCc(cc.toArray(new String[0]));
        }

        if (bcc != null && !bcc.isEmpty()) {
            helper.setBcc(bcc.toArray(new String[0]));
        }

        // Set subject and body
        helper.setSubject(subject);
        helper.setText(body, isHtml);

        // Set additional headers
        message.setHeader("X-Mailer", "PAVES Employee Leave Management System");
        message.setHeader("X-Priority", "3");

        return message;
    }
    
    /**
     * Creates a MimeMessage for multiple recipients
     */
    private MimeMessage createMimeMessage(List<String> recipients, List<String> cc, List<String> bcc, 
                                        String subject, String body, boolean isHtml) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        // Set from address
        helper.setFrom(emailProperties.getDefaultFromAddress(), emailProperties.getDefaultFromName());
        
        // Set recipients
        helper.setTo(recipients.toArray(new String[0]));
        
        if (cc != null && !cc.isEmpty()) {
            helper.setCc(cc.toArray(new String[0]));
        }
        
        if (bcc != null && !bcc.isEmpty()) {
            helper.setBcc(bcc.toArray(new String[0]));
        }
        
        // Set subject and body
        helper.setSubject(subject);
        helper.setText(body, isHtml);
        
        // Set additional headers
        message.setHeader("X-Mailer", "PAVES Employee Leave Management System");
        message.setHeader("X-Priority", "3");
        
        return message;
    }
    
    /**
     * Validates input parameters
     */
    private boolean isValidInput(String to, String subject, String body) {
        return to != null && !to.trim().isEmpty() &&
               subject != null && !subject.trim().isEmpty() &&
               body != null && !body.trim().isEmpty();
    }
    
    /**
     * Builds a string representation of all recipients
     */
    private String buildRecipientString(String to, List<String> cc, List<String> bcc) {
        StringBuilder recipients = new StringBuilder("TO: " + to);
        
        if (cc != null && !cc.isEmpty()) {
            recipients.append(", CC: ").append(String.join(", ", cc));
        }
        
        if (bcc != null && !bcc.isEmpty()) {
            recipients.append(", BCC: ").append(String.join(", ", bcc));
        }
        
        return recipients.toString();
    }
    
    /**
     * AutoCloseable implementation for resource cleanup
     */
    @Override
    public void close() {
        log.info("OutlookEmailService is being closed - performing cleanup");
        // Any additional cleanup logic can be added here
    }
}