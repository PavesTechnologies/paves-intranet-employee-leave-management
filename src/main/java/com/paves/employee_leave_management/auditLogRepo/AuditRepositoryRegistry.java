package com.paves.employee_leave_management.auditLogRepo;

import com.paves.employee_leave_management.audit_entities.LeaveTypeAuditLog;
import com.paves.employee_leave_management.auditLogRepo.LeaveTypeAuditLogRepo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuditRepositoryRegistry {
    private final Map<Class<?>, Object> repoMap = new HashMap<>();

    public AuditRepositoryRegistry(LeaveTypeAuditLogRepo leaveTypeAuditLogRepo) {
        repoMap.put(LeaveTypeAuditLog.class, leaveTypeAuditLogRepo);
        // Add other audit repos here for other entities' audit classes
    }

    @SuppressWarnings("unchecked")
    public <T> T getRepoForAuditClass(Class<?> auditClass) {
        return (T) repoMap.get(auditClass);
    }
}
