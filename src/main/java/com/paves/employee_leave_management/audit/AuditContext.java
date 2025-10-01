package com.paves.employee_leave_management.audit;

import com.paves.employee_leave_management.audit_tables.AuditRecord;

public class AuditContext {
    private static AuditService auditService;

    public static void setAuditService(AuditService service) {
        auditService = service;
    }

    public static void save(AuditRecord record) {
        if (auditService != null) {
            auditService.save(record);
        }
    }
}

