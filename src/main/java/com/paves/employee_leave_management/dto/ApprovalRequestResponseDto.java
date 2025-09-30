package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.RequestStatus;

import java.time.LocalDateTime;

/**
 * DTO for returning ApprovalRequest data to the client.
 * This prevents serialization issues with Hibernate proxies.
 */
public class ApprovalRequestResponseDto {

    private Long id;
    private ActionType actionType;
    private RequestStatus status;
    private String payload; // The raw JSON payload for the frontend to parse
    private String makerName;
    private LocalDateTime createdAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getMakerName() {
        return makerName;
    }

    public void setMakerName(String makerName) {
        this.makerName = makerName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
