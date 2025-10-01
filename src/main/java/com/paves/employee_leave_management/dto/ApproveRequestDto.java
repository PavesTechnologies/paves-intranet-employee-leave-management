package com.paves.employee_leave_management.dto;

/**
 * DTO for carrying approval-related data, such as comments from the approver.
 */
public class ApproveRequestDto {

    private String comment;

    // Getters and Setters

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
