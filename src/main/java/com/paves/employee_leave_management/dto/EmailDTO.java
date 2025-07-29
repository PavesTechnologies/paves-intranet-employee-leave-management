package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object for email operations
 */
@Setter
@Getter
public class EmailDTO {

    // Getters and Setters
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String to;
    
    private String[] cc;
    
    private String[] bcc;
    
    @NotBlank(message = "Subject is required")
    private String subject;
    
    @NotBlank(message = "Email body is required")
    private String body;
    
    private boolean isHtml = false;
    
    private String attachmentPath;
    
    // Constructors
    public EmailDTO() {}
    
    public EmailDTO(String to, String subject, String body) {
        this.to = to;
        this.subject = subject;
        this.body = body;
    }
    
    public EmailDTO(String to, String subject, String body, boolean isHtml) {
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.isHtml = isHtml;
    }

}
