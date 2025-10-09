package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "record_lock")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tableName;

    private String recordId;

    private String lockedBy;

    private LocalDateTime lockedAt;

    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
