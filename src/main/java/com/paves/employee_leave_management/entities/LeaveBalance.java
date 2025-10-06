package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_balance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners({AuditingEntityListener.class})
//@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class LeaveBalance {

    @Id
    @Column(name = "balance_id")
    private String balanceId;

    @PrePersist
    public void generateId(){
        if (balanceId == null){
            balanceId = "BAL"+ UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase();
        }
    }

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
//    @JsonIgnoreProperties({"leaveBalances"}) // include LeaveType but ignore its leaveBalances
    private LeaveType leaveType;

    @Column(name = "total_leaves", nullable = false)
    private double totalLeaves;

    @Column(name = "accrued_leaves")
    private double accruedLeaves = 0;

    @Column(name = "used_leaves")
    private double usedLeaves = 0;

    @Column(name = "remaining_leaves", nullable = false)
    private double remainingLeaves;

    @Column(name = "carried_forward")
    private double carriedForward = 0;

    @Column(name = "expired_leaves")
    private double expiredLeaves = 0;

    @Column(name = "encashed_leaves")
    private Integer encashedLeaves = 0;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_accrual_date")
    private LocalDate lastAccrualDate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createAt;

    @LastModifiedDate
    @Column(name = "last_updated_at", insertable = false)
    private LocalDateTime lastUpdatedAt;

    public void updateRemainingLeaves() {
        this.remainingLeaves = (accruedLeaves + carriedForward) - usedLeaves;
    }
}
