package com.paves.employee_leave_management.audit_tables;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityName;
    private String action;
    private String changedBy;
    private LocalDateTime changedAt;

    @Lob
    private String entityData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
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

    public String getEntityData() {
        return entityData;
    }

    public void setEntityData(String entityData) {
        this.entityData = entityData;
    }
}
