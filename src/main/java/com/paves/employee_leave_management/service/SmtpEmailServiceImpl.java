package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * SMTP implementation of EmailService using JavaMail
 * Supports Gmail, Outlook, and other SMTP providers
 */
@Service
public class SmtpEmailServiceImpl implements EmailServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailServiceImpl.class);

    @Value("${email.smtp.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${email.smtp.port:587}")
    private String smtpPort;

    @Value("${email.smtp.username}")
    private String smtpUsername;

    @Value("${email.smtp.password}")
    private String smtpPassword;

    @Value("${email.smtp.auth:true}")
    private String smtpAuth;

    @Value("${email.smtp.starttls:true}")
    private String smtpStartTls;

    @Value("${email.from.name:Leave Management System}")
    private String fromName;

    private Session getMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", smtpStartTls);
        props.put("mail.smtp.ssl.trust", smtpHost);

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });
    }

    @Override
    public boolean sendEmail(String to, String subject, String body) {
        try {
            Session session = getMailSession();
            Message message = new MimeMessage(session);

            message.setFrom(new InternetAddress(smtpUsername, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            logger.info("Email sent successfully to: {}", to);
            return true;

        } catch (Exception e) {
            logger.error("Failed to send email to: {}. Error: {}", to, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendEmail(EmailDTO emailDTO) {
        try {
            Session session = getMailSession();
            Message message = new MimeMessage(session);

            message.setFrom(new InternetAddress(smtpUsername, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailDTO.getTo()));

            if (emailDTO.getCc() != null && emailDTO.getCc().length > 0) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(String.join(",", emailDTO.getCc())));
            }

            if (emailDTO.getBcc() != null && emailDTO.getBcc().length > 0) {
                message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(String.join(",", emailDTO.getBcc())));
            }

            message.setSubject(emailDTO.getSubject());

            if (emailDTO.isHtml()) {
                message.setContent(emailDTO.getBody(), "text/html; charset=utf-8");
            } else {
                message.setText(emailDTO.getBody());
            }

            Transport.send(message);
            logger.info("Email sent successfully to: {}", emailDTO.getTo());
            return true;

        } catch (Exception e) {
            logger.error("Failed to send email to: {}. Error: {}", emailDTO.getTo(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendEmailToMultiple(String[] recipients, String subject, String body) {
        try {
            Session session = getMailSession();
            Message message = new MimeMessage(session);

            message.setFrom(new InternetAddress(smtpUsername, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(String.join(",", recipients)));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            logger.info("Email sent successfully to {} recipients", recipients.length);
            return true;

        } catch (Exception e) {
            logger.error("Failed to send email to multiple recipients. Error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendLeaveApplicationNotification(String managerEmail, String employeeName,
                                                    String leaveType, String startDate, String endDate, String reason) {
        String subject = "New Leave Application - " + employeeName;
        String body = buildLeaveApplicationEmailBody(employeeName, leaveType, startDate, endDate, reason);
        return sendEmail(managerEmail, subject, body);
    }

    @Override
    public boolean sendLeaveApprovalNotification(String employeeEmail, String employeeName,
                                                 String leaveType, String startDate, String endDate, String managerComment) {
        String subject = "Leave Application Approved - " + leaveType;
        String body = buildLeaveApprovalEmailBody(employeeName, leaveType, startDate, endDate, managerComment);
        return sendEmail(employeeEmail, subject, body);
    }

    @Override
    public boolean sendLeaveRejectionNotification(String employeeEmail, String employeeName,
                                                  String leaveType, String startDate, String endDate, String rejectionReason) {
        String subject = "Leave Application Rejected - " + leaveType;
        String body = buildLeaveRejectionEmailBody(employeeName, leaveType, startDate, endDate, rejectionReason);
        return sendEmail(employeeEmail, subject, body);
    }

    @Override
    public boolean sendLeaveRevokeNotification(String employeeEmail, String employeeName,
                                               String leaveType, String startDate, String endDate) {
        String subject = "Leave Application Rejected - " + leaveType;
        String body = buildLeaveRevokeEmailBody(employeeName, leaveType, startDate, endDate);
        return sendEmail(employeeEmail, subject, body);
    }

    @Override
    public boolean sendLeaveUpdateNotification(String employeeEmail, String employeeName,
                                               String leaveType, String startDate, String endDate, String updateDetails) {
        String subject = "Leave Application Updated - " + leaveType;
        String body = buildLeaveUpdateEmailBody(employeeName, leaveType, startDate, endDate, updateDetails);
        return sendEmail(employeeEmail, subject, body);
    }

    @Override
    public boolean sendLeaveCancellationNotification(String managerEmail, String employeeName,
                                                     String leaveType, String startDate, String endDate) {
        String subject = "Leave Cancelled - " + employeeName;
        String body = buildLeaveCancellationEmailBody(employeeName, leaveType, startDate, endDate);
        return sendEmail(managerEmail, subject, body);
    }

    // Email template methods
    private String buildLeaveApplicationEmailBody(String employeeName, String leaveType,
                                                  String startDate, String endDate, String reason) {
        return String.format("""
                Dear Manager,
                
                A new leave application has been submitted by %s.
                
                Leave Details:
                - Employee: %s
                - Leave Type: %s
                - Start Date: %s
                - End Date: %s
                - Reason: %s
                
                Please review and take appropriate action in the Leave Management System.
                
                Best regards,
                Paves Global Infotech Private Limited
                """, employeeName, employeeName, leaveType, startDate, endDate, reason);
    }

    private String buildLeaveApprovalEmailBody(String employeeName, String leaveType,
                                               String startDate, String endDate, String managerComment) {
        String commentSection = (managerComment != null && !managerComment.trim().isEmpty())
                ? "\n- Manager Comment: " + managerComment : "";

        return String.format("""
                Dear %s,
                
                Your leave application has been APPROVED.
                
                Leave Details:
                - Leave Type: %s
                - Start Date: %s
                - End Date: %s%s
                
                Please ensure proper handover of your responsibilities before your leave begins.
                
                Best regards,
                Paves Global Infotech Private Limited
                """, employeeName, leaveType, startDate, endDate, commentSection);
    }

    private String buildLeaveRejectionEmailBody(String employeeName, String leaveType,
                                                String startDate, String endDate, String rejectionReason) {
        return String.format("""
                Dear %s,
                
                Your leave application has been REJECTED.
                
                Leave Details:
                - Leave Type: %s
                - Start Date: %s
                - End Date: %s
                - Rejection Reason: %s
                
                Please contact your manager for further clarification if needed.
                
                Best regards,
                Paves Global Infotech Private Limited
                """, employeeName, leaveType, startDate, endDate, rejectionReason);
    }

    private String buildLeaveRevokeEmailBody(String employeeName, String leaveType,
                                             String startDate, String endDate) {
        return String.format("""
                Dear %s,
                
                Your leave application has been REVOKED.
                
                Leave Details:
                - Leave Type: %s
                - Start Date: %s
                - End Date: %s
                
                Please contact your manager for further clarification if needed.
                
                Best regards,
                Paves Global Infotech Private Limited
                """, employeeName, leaveType, startDate, endDate);
    }

    private String buildLeaveUpdateEmailBody(String employeeName, String leaveType,
                                             String startDate, String endDate, String updateDetails) {
        return String.format("""
                Dear %s,
                
                Your leave application has been updated by your manager.
                
                Updated Leave Details:
                - Leave Type: %s
                - Start Date: %s
                - End Date: %s
                - Update Details: %s
                
                Please review the changes in the Leave Management System.
                
                Best regards,
                Paves Global Infotech Private Limited
                """, employeeName, leaveType, startDate, endDate, updateDetails);
    }

    private String buildLeaveCancellationEmailBody(String employeeName, String leaveType,
                                                   String startDate, String endDate) {
        return String.format("""
                Dear Manager,
                
                %s has cancelled their leave application.
                
                Cancelled Leave Details:
                - Employee: %s
                - Leave Type: %s
                - Start Date: %s
                - End Date: %s
                
                No further action is required.
                
                Best regards,
                Paves Global Infotech Private Limited
                """, employeeName, employeeName, leaveType, startDate, endDate);
    }
}
