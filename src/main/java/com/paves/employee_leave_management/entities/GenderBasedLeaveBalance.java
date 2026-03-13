package com.paves.employee_leave_management.entities;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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

    // In GenderBasedLeaveBalance ✅
    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    @JsonIgnoreProperties({"leaveBalances", "hibernateLazyInitializer", "handler"}) // ignores the back-list to prevent recursion
    private GenderBasedLeave leaveType;

    @Column(name = "total_entitled_days")
    @NotNull(message = "Total entitled days cannot be null")
    private Integer totalEntitledDays;  // e.g., Maternity: 180 days

    @Column(name = "used_days", nullable = true)
    private Integer usedDays = 0;

    @Column(name = "remaining_days", nullable = true)
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
        if(totalEntitledDays == null || totalEntitledDays == 0){
            this.totalEntitledDays = 0;
            this.remainingDays = 0;
        }else{
            this.remainingDays = totalEntitledDays - usedDays;
        }
        if(usedDays == null){
            usedDays = 0;
        }
        this.createdAt = LocalDateTime.now();
    }

//    @PreUpdate
//    public void onUpdate() {
//        this.remainingDays = totalEntitledDays - usedDays;
//        this.updatedAt = LocalDateTime.now();
//    }
}

