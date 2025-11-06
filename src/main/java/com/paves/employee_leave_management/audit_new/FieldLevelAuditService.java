package com.paves.employee_leave_management.audit_new;



import com.paves.employee_leave_management.auditLogRepo.LeaveTypeAuditLogRepo;
import com.paves.employee_leave_management.auditUtils.FieldChange;
import com.paves.employee_leave_management.audit_entities.LeaveTypeAuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class FieldLevelAuditService {

    private final AuditPerEntityMapperRegistry mapperRegistry;
    private final LeaveTypeAuditLogRepo leaveTypeAuditLogRepo; // inject repos you need
    // Add other repos and wire them into constructor

    @Autowired
    public FieldLevelAuditService(AuditPerEntityMapperRegistry mapperRegistry,
                                  LeaveTypeAuditLogRepo leaveTypeAuditLogRepo) {
        this.mapperRegistry = mapperRegistry;
        this.leaveTypeAuditLogRepo = leaveTypeAuditLogRepo;
    }

    /**
     * Persist field-level changes for a given entity class (per-entity table).
     */
    @Async("auditExecutor")
    public void persistFieldChanges(Class<?> entityClass,
                                    String entityId,
                                    List<FieldChange> fieldChanges,
                                    String action,
                                    String changedBy,
                                    LocalDateTime changedAt) {

        if (fieldChanges == null || fieldChanges.isEmpty()) {
            // for INSERT/DELETE you might still want to create rows; handle below
        }

        BiConsumer<AuditPerEntityMapperRegistry.FieldChangeContext, List<Object>> mapper = mapperRegistry.getMapperFor(entityClass);
        if (mapper == null) {
            // no mapper registered for this domain entity
            return;
        }

        AuditPerEntityMapperRegistry.FieldChangeContext ctx = new AuditPerEntityMapperRegistry.FieldChangeContext(entityId, fieldChanges, action, changedBy, changedAt);
        List<Object> rows = new ArrayList<>();
        mapper.accept(ctx, rows);

        // persist rows per their type
        for (Object row : rows) {
            if (row instanceof LeaveTypeAuditLog ltRow) {
                leaveTypeAuditLogRepo.save(ltRow);
            }
            // else-if blocks for other audit entity types (add repos & saving)
        }
    }
}