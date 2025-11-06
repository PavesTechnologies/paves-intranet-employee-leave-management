package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_execution_log", indexes = {
        @Index(name = "idx_job_name", columnList = "jobName"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_start_time", columnList = "startTime")
})
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class JobExecutionLog {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    private String jobName;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String nodeIdentifier;
    private int attempt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    private int version;
}
