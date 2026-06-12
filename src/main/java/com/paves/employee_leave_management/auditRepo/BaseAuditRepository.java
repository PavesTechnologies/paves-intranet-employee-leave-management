package com.paves.employee_leave_management.auditRepo;

import com.paves.employee_leave_management.audit_tables.BaseAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseAuditRepository<T extends BaseAuditEntity> extends JpaRepository<T, Long> {
}