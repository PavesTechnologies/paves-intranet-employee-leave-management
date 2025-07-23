package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

// LeaveBalance Entity
@Entity
@Table(name = "leave_balance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @JsonManagedReference
    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "total_leaves", nullable = false)
    private double totalLeaves;

    @Builder.Default
    @Column(name = "accrued_leaves", columnDefinition = "INT DEFAULT 0")
    private double accruedLeaves = 0;

    @Builder.Default
    @Column(name = "used_leaves", columnDefinition = "INT DEFAULT 0")
    private double usedLeaves = 0;

    @Column(name = "remaining_leaves", nullable = false)
    private double remainingLeaves;

    @Builder.Default
    @Column(name = "carried_forward", columnDefinition = "INT DEFAULT 0")
    private double carriedForward = 0;

    @Builder.Default
    @Column(name = "expired_leaves", columnDefinition = "INT DEFAULT 0")
    private double expiredLeaves = 0;

    @Builder.Default
    @Column(name = "encashed_leaves", columnDefinition = "INT DEFAULT 0")
    private Integer encashedLeaves = 0;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_accrual_date")
    private LocalDate lastAccrualDate;

    // Custom constructor for essential fields
    public LeaveBalance(Employee employee, LeaveType leaveType, Integer totalLeaves, Integer year) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.totalLeaves = totalLeaves;
        this.year = year;
        this.remainingLeaves = totalLeaves;
        this.accruedLeaves = 0;
        this.usedLeaves = 0;
        this.carriedForward = 0;
        this.expiredLeaves = 0;
        this.encashedLeaves = 0;
    }

    // Business logic methods
    public double getAvailableBalance() {
        return accruedLeaves + carriedForward - usedLeaves - expiredLeaves;
    }

    public void updateRemainingLeaves() {
        this.remainingLeaves = totalLeaves + carriedForward - usedLeaves;
    }
}

