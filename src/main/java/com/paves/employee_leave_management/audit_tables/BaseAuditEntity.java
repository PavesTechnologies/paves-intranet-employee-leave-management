package com.paves.employee_leave_management.audit_tables;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@MappedSuperclass
@Data
public abstract class BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;        // INSERT / UPDATE / DELETE
    private String changedBy;
    private LocalDateTime changedAt;
}
