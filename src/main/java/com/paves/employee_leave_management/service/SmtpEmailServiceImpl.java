package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Map;

@Service
public class SmtpEmailServiceImpl implements EmailServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Value("${email.from.name:Leave Management System}")
    private String fromName;

    @Override
    public boolean sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", to);
            return true;
        } catch (MessagingException e) {
            logger.error("Failed to send email to: {}. Error: {}", to, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendEmail(EmailDTO emailDTO) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(emailDTO.getTo());
            if (emailDTO.getCc() != null) {
                helper.setCc(emailDTO.getCc());
            }
            if (emailDTO.getBcc() != null) {
                helper.setBcc(emailDTO.getBcc());
            }
            helper.setSubject(emailDTO.getSubject());
            helper.setText(emailDTO.getBody(), emailDTO.isHtml());
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", emailDTO.getTo());
            return true;
        } catch (MessagingException e) {
            logger.error("Failed to send email to: {}. Error: {}", emailDTO.getTo(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendEmailToMultiple(String[] recipients, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            logger.info("Email sent successfully to {} recipients", recipients.length);
            return true;
        } catch (MessagingException e) {
            logger.error("Failed to send email to multiple recipients. Error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendEmailWithAttachment(String to, String subject, String body, Resource attachment, String attachmentName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.addAttachment(attachmentName, attachment);
            mailSender.send(message);
            logger.info("Email with attachment sent successfully to: {}", to);
            return true;
        } catch (MessagingException e) {
            logger.error("Failed to send email with attachment to: {}. Error: {}", to, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendEmailFromTemplate(String to, String subject, String templateName, Map<String, Object> templateModel) {
        Context context = new Context();
        context.setVariables(templateModel);
        String htmlBody = templateEngine.process(templateName, context);
        return sendEmail(to, subject, htmlBody);
    }

    @Override
    public boolean sendLeaveApplicationNotification(String managerEmail, String employeeName,
                                                    String leaveType, String startDate, String endDate, String reason) {
        String subject = "New Leave Application - " + employeeName;
        Map<String, Object> templateModel = Map.of(
                "employeeName", employeeName,
                "leaveType", leaveType,
                "startDate", startDate,
                "endDate", endDate,
                "reason", reason
        );
        return sendEmailFromTemplate(managerEmail, subject, "leave-application-notification.html", templateModel);
    }

    @Override
    public boolean sendLeaveApprovalNotification(String employeeEmail, String employeeName,
                                                 String leaveType, String startDate, String endDate, String managerComment) {
        String subject = "Leave Application Approved - " + leaveType;
        Map<String, Object> templateModel = Map.of(
                "employeeName", employeeName,
                "leaveType", leaveType,
                "startDate", startDate,
                "endDate", endDate,
                "managerComment", managerComment
        );
        return sendEmailFromTemplate(employeeEmail, subject, "leave-approval-notification.html", templateModel);
    }

    @Override
    public boolean sendLeaveRejectionNotification(String employeeEmail, String employeeName,
                                                  String leaveType, String startDate, String endDate, String rejectionReason) {
        String subject = "Leave Application Rejected - " + leaveType;
        Map<String, Object> templateModel = Map.of(
                "employeeName", employeeName,
                "leaveType", leaveType,
                "startDate", startDate,
                "endDate", endDate,
                "rejectionReason", rejectionReason
        );
        return sendEmailFromTemplate(employeeEmail, subject, "leave-rejection-notification.html", templateModel);
    }

    @Override
    public boolean sendLeaveRevokeNotification(String employeeEmail, String employeeName,
                                               String leaveType, String startDate, String endDate) {
        String subject = "Leave Application Revoked - " + leaveType;
        Map<String, Object> templateModel = Map.of(
                "employeeName", employeeName,
                "leaveType", leaveType,
                "startDate", startDate,
                "endDate", endDate
        );
        return sendEmailFromTemplate(employeeEmail, subject, "leave-revocation-notification.html", templateModel);
    }

    @Override
    public boolean sendLeaveUpdateNotification(String employeeEmail, String employeeName,
                                               String leaveType, String startDate, String endDate, String updateDetails) {
        String subject = "Leave Application Updated - " + leaveType;
        Map<String, Object> templateModel = Map.of(
                "employeeName", employeeName,
                "leaveType", leaveType,
                "startDate", startDate,
                "endDate", endDate,
                "updateDetails", updateDetails
        );
        return sendEmailFromTemplate(employeeEmail, subject, "leave-update-notification.html", templateModel);
    }

    @Override
    public boolean sendLeaveCancellationNotification(String managerEmail, String employeeName,
                                                     String leaveType, String startDate, String endDate) {
        String subject = "Leave Cancelled - " + employeeName;
        Map<String, Object> templateModel = Map.of(
                "employeeName", employeeName,
                "leaveType", leaveType,
                "startDate", startDate,
                "endDate", endDate
        );
        return sendEmailFromTemplate(managerEmail, subject, "leave-cancellation-notification.html", templateModel);
    }

    @Override
    public boolean sendPendingApprovalReminder(String managerEmail, String employeeName, String leaveType, String startDate, String endDate) {
        String subject = "Reminder: Pending Leave Approval - " + employeeName;
        Map<String, Object> templateModel = Map.of(
                "recipientName", "Manager",
                "messageBody", "This is a reminder that a leave request from " + employeeName + " is pending your approval.",
                "detailsTitle", "Leave Request Details",
                "details", Map.of(
                        "Employee Name", employeeName,
                        "Leave Type", leaveType,
                        "Start Date", startDate,
                        "End Date", endDate
                ),
                "closingMessage", "Please log in to the system to approve or reject the request."
        );
        return sendEmailFromTemplate(managerEmail, subject, "pending-approval-reminder.html", templateModel);
    }

    @Override
    public boolean sendOverdueApprovalEscalation(String managerEmail, String employeeName, String leaveType, String startDate, String endDate) {
        String subject = "Escalation: Overdue Leave Approval - " + employeeName;
        Map<String, Object> templateModel = Map.of(
                "recipientName", "Manager",
                "messageBody", "This is an escalation that a leave request from " + employeeName + " is overdue for approval.",
                "detailsTitle", "Overdue Leave Request Details",
                "details", Map.of(
                        "Employee Name", employeeName,
                        "Leave Type", leaveType,
                        "Start Date", startDate,
                        "End Date", endDate
                ),
                "closingMessage", "Please log in to the system to approve or reject the request immediately."
        );
        return sendEmailFromTemplate(managerEmail, subject, "pending-approval-reminder.html", templateModel);
    }

    @Override
    public boolean sendPendingApprovalReminderDigest(String managerEmail, List<LeaveRequest> requests) {
        String subject = "Pending Leave Approval Digest";
        Map<String, Object> templateModel = Map.of(
                "managerName", "Manager",
                "requests", requests
        );
        return sendEmailFromTemplate(managerEmail, subject, "pending-approval-digest.html", templateModel);
    }

    @Override
    public boolean sendOverdueApprovalEscalationDigest(String managerEmail, List<LeaveRequest> requests) {
        String subject = "Overdue Leave Approval Escalation Digest";
        Map<String, Object> templateModel = Map.of(
                "managerName", "Manager",
                "requests", requests
        );
        return sendEmailFromTemplate(managerEmail, subject, "overdue-approval-digest.html", templateModel);
    }
}