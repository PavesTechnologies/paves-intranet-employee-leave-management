package com.paves.employee_leave_management.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

// LeaveBalance Entity
@Entity
@Table(name = "leave_balance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LeaveBalance {

    @Id
    @Column(name = "balance_id")
    private String balanceId;

    @PrePersist
    public void generateId(){
        if (balanceId == null){
            balanceId = "BAL"+UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase();
        }
    }

    @ManyToOne
    @JsonManagedReference
    @JsonIgnore
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "total_leaves", nullable = false)
    private double totalLeaves;

    @Builder.Default
    @Column(name = "accrued_leaves")
    private double accruedLeaves = 0;

    @Builder.Default
    @Column(name = "used_leaves")
    private double usedLeaves = 0;

    @Column(name = "remaining_leaves", nullable = false)
    private double remainingLeaves;

    @Builder.Default
    @Column(name = "carried_forward")
    private double carriedForward = 0;

    @Builder.Default
    @Column(name = "expired_leaves")
    private double expiredLeaves = 0;

    @Builder.Default
    @Column(name = "encashed_leaves")
    private Integer encashedLeaves = 0;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_accrual_date")
    private LocalDate lastAccrualDate;


    public void updateRemainingLeaves() {
        this.remainingLeaves = (accruedLeaves+carriedForward) - usedLeaves;
    }
}

