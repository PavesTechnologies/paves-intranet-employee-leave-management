package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;

import java.util.List;

/**
 * Email service interface for sending notifications
 * This abstraction allows easy switching between email providers
 */
public interface EmailServiceInterface {
    
    /**
     * Send a simple email
     * @param to recipient email address
     * @param subject email subject
     * @param body email content (can be HTML or plain text)
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmail(String to, String subject, String body);
    
    /**
     * Send email using EmailDTO
     * @param emailDTO email data transfer object
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmail(EmailDTO emailDTO);
    
    /**
     * Send email to multiple recipients
     * @param recipients array of email addresses
     * @param subject email subject
     * @param body email content
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmailToMultiple(String[] recipients, String subject, String body);
    
    /**
     * Send leave application notification to manager
     * @param managerEmail manager's email address
     * @param employeeName employee who applied for leave
     * @param leaveType type of leave
     * @param startDate leave start date
     * @param endDate leave end date
     * @param reason leave reason
     * @return true if email sent successfully
     */
    boolean sendLeaveApplicationNotification(String managerEmail, String employeeName, 
                                           String leaveType, String startDate, String endDate, String reason);
    
    /**
     * Send leave approval notification to employee
     * @param employeeEmail employee's email address
     * @param employeeName employee name
     * @param leaveType type of leave
     * @param startDate leave start date
     * @param endDate leave end date
     * @param managerComment optional manager comment
     * @return true if email sent successfully
     */
    boolean sendLeaveApprovalNotification(String employeeEmail, String employeeName,
                                        String leaveType, String startDate, String endDate, String managerComment);
    
    /**
     * Send leave rejection notification to employee
     * @param employeeEmail employee's email address
     * @param employeeName employee name
     * @param leaveType type of leave
     * @param startDate leave start date
     * @param endDate leave end date
     * @param rejectionReason reason for rejection
     * @return true if email sent successfully
     */
    boolean sendLeaveRejectionNotification(String employeeEmail, String employeeName,
                                         String leaveType, String startDate, String endDate, String rejectionReason);
    
    /**
     * Send leave update notification to employee
     * @param employeeEmail employee's email address
     * @param employeeName employee name
     * @param leaveType type of leave
     * @param startDate leave start date
     * @param endDate leave end date
     * @param updateDetails details of what was updated
     * @return true if email sent successfully
     */
    boolean sendLeaveUpdateNotification(String employeeEmail, String employeeName,
                                      String leaveType, String startDate, String endDate, String updateDetails);
    
    /**
     * Send leave cancellation notification to manager
     * @param managerEmail manager's email address
     * @param employeeName employee who cancelled leave
     * @param leaveType type of leave
     * @param startDate leave start date
     * @param endDate leave end date
     * @return true if email sent successfully
     */
    boolean sendLeaveCancellationNotification(String managerEmail, String employeeName,
                                            String leaveType, String startDate, String endDate);

    interface ApprovalService {

        void submitForApproval(MCApprovalRequestDto dto, Employee maker, String makerRole);

        List<ApprovalRequestResponseDto> getPendingApprovalsForUser(Employee approver);

        void approveRequest(Long requestId, ApproveRequestDto dto, Employee checker);

        void rejectRequest(Long requestId, RejectRequestDto dto, Employee checker);
    }
}
