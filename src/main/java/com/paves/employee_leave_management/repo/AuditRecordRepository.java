package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.audit_tables.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
}
