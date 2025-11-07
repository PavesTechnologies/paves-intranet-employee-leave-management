package com.paves.employee_leave_management.auditLogRepo;

import com.paves.employee_leave_management.audit_entities.BaseAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;

@NoRepositoryBean
public interface BaseAuditRepository <T extends BaseAuditEntity> extends JpaRepository<T, String> {
}
