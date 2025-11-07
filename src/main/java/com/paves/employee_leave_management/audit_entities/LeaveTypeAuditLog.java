package com.paves.employee_leave_management.audit_entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "leave_type_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveTypeAuditLog extends BaseAuditEntity {

    @Id
    @Column(name = "audit_id", length = 50)
    private String auditId;

    @Column(name = "leave_type_id", nullable = false)
    private String leaveTypeId;

    @Column(name = "field_name", nullable = false, length = 150)
    private String fieldName;

    @Lob
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Lob
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "action", length = 20)
    private String action; // INSERT/UPDATE/DELETE

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;
}
