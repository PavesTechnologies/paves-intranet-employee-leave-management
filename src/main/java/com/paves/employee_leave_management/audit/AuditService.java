package com.paves.employee_leave_management.audit;

import com.paves.employee_leave_management.audit_tables.AuditRecord;
import com.paves.employee_leave_management.repo.AuditRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRecordRepository repo;

    public AuditService(AuditRecordRepository repo) {
        this.repo = repo;
        AuditContext.setAuditService(this); // register service
    }

    public void save(AuditRecord record) {
        repo.save(record);
    }
}

