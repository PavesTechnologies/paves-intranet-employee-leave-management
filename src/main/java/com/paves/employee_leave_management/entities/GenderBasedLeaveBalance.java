package com.paves.employee_leave_management.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gender_leave_balance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenderBasedLeaveBalance {

    @Id
    @Column(name = "balance_id")
    private String balanceId;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @Column(name = "total_entitled_days", nullable = false)
    private Integer totalEntitledDays;  // e.g., Maternity: 180 days

    @Column(name = "used_days", nullable = false)
    private Integer usedDays = 0;

    @Column(name = "remaining_days", nullable = false)
    private Integer remainingDays;

    @Column(name = "year")
    private Integer year;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "times_used")
    private Integer timesUsed;

    @PrePersist
    public void onCreate() {
        if (balanceId == null) {
            balanceId = "GBLB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        this.remainingDays = totalEntitledDays - usedDays;
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.remainingDays = totalEntitledDays - usedDays;
        this.updatedAt = LocalDateTime.now();
    }
}

