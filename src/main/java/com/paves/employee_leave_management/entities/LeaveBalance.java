package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.paves.employee_leave_management.audit.AuditEntityListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// LeaveBalance Entity
@Entity
@Table(name = "leave_balance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners({AuditEntityListener.class})
//@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@ToString
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LeaveBalance {

    @Id
    @Column(name = "balance_id")
    private String balanceId;
    @ManyToOne
    @JsonManagedReference
//    @JsonIgnore
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Column(name = "emp_id")
    private String employeeId;
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
    @Column(name = "is_blocked")
    private Boolean isBlocked;
    @Column(name = "block_id")
    private String blockId;
    //   @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createAt;
    // @LastModifiedDate
    @Column(name = "last_updated_at", insertable = false)
    private LocalDateTime lastUpdatedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @PrePersist
    public void generateId() {
        if (balanceId == null) {
            balanceId = "BAL" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }


    // in LeaveRequest entity

//    @Version
//    @Column(name = "version")
//    private Long version;

    public void updateRemainingLeaves() {
        this.remainingLeaves = (accruedLeaves + carriedForward) - usedLeaves;
    }
}

