package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_leave_type_update")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledLeaveTypeUpdate {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "leave_type_id", nullable = false)
    private String leaveTypeId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Lob
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    public void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.PENDING;
    }

    public enum Status {
        PENDING, APPLIED, FAILED, CANCELLED
    }
}
