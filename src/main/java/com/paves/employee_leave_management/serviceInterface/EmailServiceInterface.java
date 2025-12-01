package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

/**
 * Email service interface for sending notifications
 * This abstraction allows easy switching between email providers
 */
public interface EmailServiceInterface {

    /**
     * Send a simple email
     *
     * @param to      recipient email address
     * @param subject email subject
     * @param body    email content (can be HTML or plain text)
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmail(String to, String subject, String body);

    /**
     * Send email using EmailDTO
     *
     * @param emailDTO email data transfer object
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmail(EmailDTO emailDTO);

    /**
     * Send email to multiple recipients
     *
     * @param recipients array of email addresses
     * @param subject    email subject
     * @param body       email content
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmailToMultiple(String[] recipients, String subject, String body);

    /**
     * Send email with attachment
     *
     * @param to          recipient email address
     * @param subject     email subject
     * @param body        email content
     * @param attachment  attachment
     * @param attachmentName attachment name
     * @return true if email sent successfully
     */
    boolean sendEmailWithAttachment(String to, String subject, String body, Resource attachment, String attachmentName);

    /**
     * Send email using a template
     *
     * @param to          recipient email address
     * @param subject     email subject
     * @param templateName template name
     * @param templateModel template model
     * @return true if email sent successfully
     */
    boolean sendEmailFromTemplate(String to, String subject, String templateName, Map<String, Object> templateModel);

    /**
     * Send leave application notification to manager
     *
     * @param managerEmail manager's email address
     * @param employeeName employee who applied for leave
     * @param leaveType    type of leave
     * @param startDate    leave start date
     * @param endDate      leave end date
     * @param reason       leave reason
     * @return true if email sent successfully
     */
    boolean sendLeaveApplicationNotification(String managerEmail, String employeeName,
                                             String leaveType, String startDate, String endDate, String reason);

    /**
     * Send leave approval notification to employee
     *
     * @param employeeEmail  employee's email address
     * @param employeeName   employee name
     * @param leaveType      type of leave
     * @param startDate      leave start date
     * @param endDate        leave end date
     * @param managerComment optional manager comment
     * @return true if email sent successfully
     */
    boolean sendLeaveApprovalNotification(String employeeEmail, String employeeName,
                                          String leaveType, String startDate, String endDate, String managerComment);

    /**
     * Send leave rejection notification to employee
     *
     * @param employeeEmail   employee's email address
     * @param employeeName    employee name
     * @param leaveType       type of leave
     * @param startDate       leave start date
     * @param endDate         leave end date
     * @param rejectionReason reason for rejection
     * @return true if email sent successfully
     */
    boolean sendLeaveRejectionNotification(String employeeEmail, String employeeName,
                                           String leaveType, String startDate, String endDate, String rejectionReason);

    /**
     * Send leave revoke notification to employee
     *
     * @param employeeEmail employee's email address
     * @param employeeName  employee name
     * @param leaveType     type of leave
     * @param startDate     leave start date
     * @param endDate       leave end date
     * @return true if email sent successfully
     */
    boolean sendLeaveRevokeNotification(String employeeEmail, String employeeName,
                                        String leaveType, String startDate, String endDate);

    /**
     * Send leave update notification to employee
     *
     * @param employeeEmail employee's email address
     * @param employeeName  employee name
     * @param leaveType     type of leave
     * @param startDate     leave start date
     * @param endDate       leave end date
     * @param updateDetails details of what was updated
     * @return true if email sent successfully
     */
    boolean sendLeaveUpdateNotification(String employeeEmail, String employeeName,
                                        String leaveType, String startDate, String endDate, String updateDetails);

    /**
     * Send leave cancellation notification to manager
     *
     I have updated the `EmailServiceInterface`. Now I need to implement the new methods in the `SmtpEmailServiceImpl` class.
     * @param managerEmail manager's email address
     * @param employeeName employee who cancelled leave
     * @param leaveType    type of leave
     * @param startDate    leave start date
     * @param endDate      leave end date
     * @return true if email sent successfully
     */
    boolean sendLeaveCancellationNotification(String managerEmail, String employeeName,
                                              String leaveType, String startDate, String endDate);
    /**
     * Sends a reminder notification to the manager for a pending leave approval.
     *
     * @param managerEmail The email address of the manager.
     * @param employeeName The name of the employee who requested the leave.
     * @param leaveType    The type of leave requested.
     * @param startDate    The start date of the leave.
     * @param endDate      The end date of the leave.
     * @return {@code true} if the email was sent successfully, {@code false} otherwise.
     */
    boolean sendPendingApprovalReminder(String managerEmail, String employeeName, String leaveType, String startDate, String endDate);

    /**
     * Sends an escalation notification for an overdue leave approval.
     *
     * @param managerEmail The email address of the manager who needs to approve the leave.
     * @param employeeName The name of the employee who requested the leave.
     * @param leaveType    The type of leave requested.
     * @param startDate    The start date of the leave.
     * @param endDate      The end date of the leave.
     * @return {@code true} if the email was sent successfully, {@code false} otherwise.
     */
    boolean sendOverdueApprovalEscalation(String managerEmail, String employeeName, String leaveType, String startDate, String endDate);

    /**
     * Sends a digest email to a manager with a list of pending leave requests.
     *
     * @param managerEmail The email address of the manager.
     * @param requests     A list of pending leave requests.
     * @return {@code true} if the email was sent successfully, {@code false} otherwise.
     */
    boolean sendPendingApprovalReminderDigest(String managerEmail, List<LeaveRequest> requests);

    /**
     * Sends a digest email to a manager with a list of overdue leave requests.
     *
     * @param managerEmail The email address of the manager.
     * @param requests     A list of overdue leave requests.
     * @return {@code true} if the email was sent successfully, {@code false} otherwise.
     */
    boolean sendOverdueApprovalEscalationDigest(String managerEmail, List<LeaveRequest> requests);

    /**
     * Send email to multiple recipients using BCC
     *
     * @param recipients array of email addresses
     * @param subject    email subject
     * @param body       email content
     * @return true if email sent successfully, false otherwise
     */
    boolean sendBulkEmail(String[] recipients, String subject, String body);

    /**
     * Send email to multiple recipients using a template and BCC
     *
     * @param recipients    array of email addresses
     * @param subject       email subject
     * @param templateName  template name
     * @param templateModel template model
     * @return true if email sent successfully
     */
    boolean sendBulkEmailFromTemplate(String[] recipients, String subject, String templateName, Map<String, Object> templateModel);
}