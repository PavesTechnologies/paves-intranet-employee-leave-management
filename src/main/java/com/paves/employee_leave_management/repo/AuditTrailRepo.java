package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditTrailRepo extends JpaRepository<AuditTrail, Long> {

    List<AuditTrail> findByTableName(String tableName);  // Use the correct field name

    List<AuditTrail> findByPerformedBy(String performedBy);

    List<AuditTrail> findByActionType(AuditTrail.ActionType actionType);
}
