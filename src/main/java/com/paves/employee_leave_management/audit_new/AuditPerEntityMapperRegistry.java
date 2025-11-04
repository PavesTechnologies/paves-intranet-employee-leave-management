package com.paves.employee_leave_management.audit_new;

import com.paves.employee_leave_management.auditLogRepo.LeaveTypeAuditLogRepo;
import com.paves.employee_leave_management.auditUtils.FieldChange;
import com.paves.employee_leave_management.audit_entities.LeaveTypeAuditLog;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;

@Component
public class AuditPerEntityMapperRegistry {

    private final Map<Class<?>, BiConsumer<FieldChangeContext, List<Object>>> registry = new HashMap<>();

    public AuditPerEntityMapperRegistry(LeaveTypeAuditLogRepo leaveTypeRepo) {
        // Register LeaveType mapper
        registry.put(com.paves.employee_leave_management.entities.LeaveType.class,
                (ctx, outputList) -> {
                    FieldChangeContext m = ctx;
                    // create a LeaveTypeAuditLog row for each FieldChange
                    for (FieldChange fc : m.getFieldChanges()) {
                        LeaveTypeAuditLog row = LeaveTypeAuditLog.builder()
                                .auditId(UUID.randomUUID().toString())
                                .leaveTypeId(m.getEntityId())
                                .fieldName(fc.getFieldName())
                                .oldValue(toJson(fc.getOldValue()))
                                .newValue(toJson(fc.getNewValue()))
                                .action(m.getAction())
                                .changedBy(m.getChangedBy())
                                .changedAt(m.getChangedAt())
                                .build();
                        outputList.add(row);
                    }
                    // repository will be used by service to save these rows (we add mapping here mainly to build objects)
                });
        // When adding other entities: register similar mapping and ensure repo mapping exists in AuditRepositoryRegistry
    }

    public BiConsumer<FieldChangeContext, List<Object>> getMapperFor(Class<?> entityClass) {
        return registry.get(entityClass);
    }

    private String toJson(Object o) {
        try { return o == null ? null : com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(o); }
        catch (Exception e) { return String.valueOf(o); }
    }

    // small context class used when building per-entity rows
    public static class FieldChangeContext {
        private final String entityId;
        private final List<FieldChange> fieldChanges;
        private final String action;
        private final String changedBy;
        private final LocalDateTime changedAt;
        public FieldChangeContext(String entityId, List<FieldChange> fieldChanges, String action, String changedBy, LocalDateTime changedAt){
            this.entityId = entityId; this.fieldChanges = fieldChanges; this.action = action; this.changedBy = changedBy; this.changedAt = changedAt;
        }
        public String getEntityId(){return entityId;}
        public List<FieldChange> getFieldChanges(){return fieldChanges;}
        public String getAction(){return action;}
        public String getChangedBy(){return changedBy;}
        public LocalDateTime getChangedAt(){return changedAt;}
    }
}
