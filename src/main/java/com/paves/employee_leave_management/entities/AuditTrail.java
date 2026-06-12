package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_trail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @Column(nullable = false)
    private String tableName;

    @Column(nullable = false)
    private String recordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    @Column(columnDefinition = "JSON")
    private String oldValues;

    @Column(columnDefinition = "JSON")
    private String newValues;

    @Column(nullable = false)
    private String performedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    public void prePersist() {
        this.changedAt = LocalDateTime.now();
    }

    public enum ActionType {
        INSERT, UPDATE, DELETE
    }
}
