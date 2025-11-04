package com.paves.employee_leave_management.audit_new;

import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

public class AuditPayload {
    private final Class<?> entityClass;
    private final String entityId;
    private final Object beforeSnapshot; // flat DTO / map
    private final Object afterSnapshot;
    private final String action; // INSERT/UPDATE/DELETE
    private final String changedBy;
    private final LocalDateTime changedAt;

    public AuditPayload(Class<?> entityClass, String entityId, Object beforeSnapshot,
                        Object afterSnapshot, String action, String changedBy) {
        this.entityClass = entityClass;
        this.entityId = entityId;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.action = action;
        this.changedBy = changedBy;
        this.changedAt = LocalDateTime.now();
    }
    // getters...
    public Class<?> getEntityClass(){return entityClass;}
    public String getEntityId(){return entityId;}
    public Object getBeforeSnapshot(){return beforeSnapshot;}
    public Object getAfterSnapshot(){return afterSnapshot;}
    public String getAction(){return action;}
    public String getChangedBy(){return changedBy;}
    public LocalDateTime getChangedAt(){return changedAt;}
}