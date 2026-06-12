package com.paves.employee_leave_management.audit_entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

@MappedSuperclass
public class BaseAuditEntity {

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "action")
    private String action;

    @Column(name = "Changed_by")
    private String changedBy;

    @Column(name = "Changed_at")
    private LocalDateTime changedAt;

    @Column(name = "changes_json", columnDefinition = "TEXT")
    private String changesJson;   // JSON with field diffs on UPDATE

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangesJson() {
        return changesJson;
    }

    public void setChangesJson(String changesJson) {
        this.changesJson = changesJson;
    }
}
