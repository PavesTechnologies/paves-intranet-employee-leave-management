package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.ActionType;

import java.util.Map;

/**
 * Data Transfer Object for submitting a request to the Maker-Checker approval workflow.
 */
public class MCApprovalRequestDto {

    private ActionType actionType;
    private Map<String, Object> payload;
    private String entityId;

    // Getters and Setters

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
}
