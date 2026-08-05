package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_balance_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceJob {

    @Id
    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @Column(name = "leave_type_name")
    private String leaveTypeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private JobStatus status;

    @Column(name = "total_employees")
    private Integer totalEmployees;

    @Column(name = "processed_employees")
    private Integer processedEmployees;

    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by")
    private String createdBy;

    // Bumped on every save via @PrePersist/@PreUpdate — used to detect a RUNNING job whose
    // worker thread died mid-run (no progress for N minutes) so it can be safely resumed.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Optimistic lock — lets the startup-recovery listener and the periodic resume sweep both
    // scan for the same stuck job without risking two threads/nodes processing it concurrently.
    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    public void onCreate() {
        if (jobId == null) jobId = UUID.randomUUID().toString();
        if (processedEmployees == null) processedEmployees = 0;
        if (progressPercentage == null) progressPercentage = 0;
        if (startedAt == null) startedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED, ROLLED_BACK
    }
}