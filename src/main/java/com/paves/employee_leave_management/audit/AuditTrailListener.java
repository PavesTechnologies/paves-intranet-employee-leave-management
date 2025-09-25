package com.paves.employee_leave_management.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.entities.AuditTrail;
import com.paves.employee_leave_management.entities.AuditTrail.ActionType;
import com.paves.employee_leave_management.repo.AuditTrailRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.persistence.*;

public class AuditTrailListener {

    private static AuditTrailRepo auditTrailRepo;
    private static ObjectMapper objectMapper;

    @Autowired
    public void init(AuditTrailRepo repo, ObjectMapper mapper) {
        auditTrailRepo = repo;
        objectMapper = mapper;
    }

    @PrePersist
    public void prePersist(Object entity) {
        log(entity, ActionType.INSERT, null);
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        try {
            Object oldEntity = getOldEntity(entity);
            log(entity, ActionType.UPDATE, oldEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreRemove
    public void preRemove(Object entity) {
        log(entity, ActionType.DELETE, entity);
    }

    private void log(Object entity, ActionType actionType, Object oldEntity) {
        try {
            String tableName = entity.getClass().getSimpleName();
            String recordId = getId(entity);
            String user = getCurrentUser();

            String oldValues = oldEntity != null ? objectMapper.writeValueAsString(oldEntity) : null;
            String newValues = actionType == ActionType.DELETE ? null : objectMapper.writeValueAsString(entity);

            AuditTrail audit = AuditTrail.builder()
                    .tableName(tableName)
                    .recordId(recordId)
                    .actionType(actionType)
                    .oldValues(oldValues)
                    .newValues(newValues)
                    .performedBy(user)
                    .build();

            auditTrailRepo.save(audit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getId(Object entity) {
        try {
            for (var field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    return String.valueOf(field.get(entity));
                }
            }
            throw new RuntimeException("No @Id field found for " + entity.getClass().getSimpleName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get entity ID for audit", e);
        }
    }

    private String getCurrentUser() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "SYSTEM";
    }

    private Object getOldEntity(Object entity) {
        // Optional: Fetch old entity from DB to compare old values
        // Can be implemented via repository findById if needed
        return null;
    }
}
