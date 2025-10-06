package com.paves.employee_leave_management.dto;

/**
 * DTO for carrying the mandatory reason for rejecting an approval request.
 */
public class RejectRequestDto {

    private String reason;

    // Getters and Setters

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
