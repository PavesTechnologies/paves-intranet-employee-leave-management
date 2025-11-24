package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "shedlock")
@Data
public class ShedLock {
    
    @Id
    @Column(name = "name", nullable = false, length = 64)
    private String name;
    
    @Column(name = "lock_until", nullable = false, columnDefinition = "TIMESTAMP(3)")
    private LocalDateTime lockUntil;
    
    @Column(name = "locked_at", nullable = false, columnDefinition = "TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3)")
    private LocalDateTime lockedAt;
    
    @Column(name = "locked_by", nullable = false, length = 255)
    private String lockedBy;
}
