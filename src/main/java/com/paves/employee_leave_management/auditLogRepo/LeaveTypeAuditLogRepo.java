package com.paves.employee_leave_management.auditLogRepo;

import com.paves.employee_leave_management.audit_entities.LeaveTypeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveTypeAuditLogRepo extends JpaRepository<LeaveTypeAuditLog, String>{
}
