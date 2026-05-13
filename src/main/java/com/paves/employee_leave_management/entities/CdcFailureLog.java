package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cdc_failure_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CdcFailureLog {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "employee_uuid", length = 36)
    private String employeeUuid;

    @Column(name = "employee_id", length = 20)
    private String employeeId;

    @Column(name = "operation", length = 10)
    private String operation; // c, u, d, r

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false)
    private FailureType failureType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CdcFailureStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_retried_at")
    private LocalDateTime lastRetriedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "ts_ms")
    private Long tsMs;

    @PrePersist
    public void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (status == null) status = CdcFailureStatus.FAILED;
        if (retryCount == null) retryCount = 0;
        if (maxRetries == null) maxRetries = 3;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum FailureType {
        UPSERT_FAILED,
        DELETE_FAILED,
        LEAVE_BALANCE_FAILED,
        MANAGER_LINK_FAILED,
        HR_LINK_FAILED,
        PARSE_FAILED
    }

    public enum CdcFailureStatus {
        FAILED,
        RETRYING,
        RESOLVED,
        EXHAUSTED
    }
}