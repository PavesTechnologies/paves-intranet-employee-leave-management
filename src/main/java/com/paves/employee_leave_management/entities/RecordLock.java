package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "record_lock",
        uniqueConstraints = @UniqueConstraint(name = "uk_table_record", columnNames = {"table_name", "record_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordLock {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "table_name", nullable = false, length = 128)
    private String tableName;

    @Column(name = "record_id", nullable = false, length = 128)
    private String recordId;

    @Column(name = "locked_by", nullable = false, length = 128)
    private String lockedBy;

    @Column(name = "employee_id", length = 128)
    private String employeeId;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
