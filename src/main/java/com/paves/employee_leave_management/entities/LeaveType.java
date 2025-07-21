package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// LeaveType Entity
@Entity
@Table(name = "leave_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"leaveRequests", "leaveBalances"})
@EqualsAndHashCode(exclude = {"leaveRequests", "leaveBalances"})
public class LeaveType {

    @Id
    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @PrePersist
    public void generateId(){
        if (leaveTypeId==null) {
            leaveTypeId = "L" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    @Column(name = "leave_name", length = 50, nullable = false)
    private String leaveName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_days_per_year")
    private Integer maxDaysPerYear;

    @Builder.Default
    @Column(name = "max_carry_forward", columnDefinition = "INT DEFAULT 0")
    private Integer maxCarryForward = 0;

    @Builder.Default
    @Column(name = "requires_documentation", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean requiresDocumentation = false;

    @Column(name = "accrual_rate", precision = 3, scale = 2)
    private BigDecimal accrualRate;

    @Column(name = "accrual_frequency", length = 20)
    private String accrualFrequency;

    @Column(name = "expiry_days")
    private Integer expiryDays;

    @Builder.Default
    @Column(name = "waiting_period_days", columnDefinition = "INT DEFAULT 0")
    private Integer waitingPeriodDays = 0;

    @Builder.Default
    @Column(name = "advance_notice_days", columnDefinition = "INT DEFAULT 0")
    private Integer advanceNoticeDays = 0;

    @Builder.Default
    @Column(name = "past_date_limit_days", columnDefinition = "INT DEFAULT 0")
    private Integer pastDateLimitDays = 0;

    @Builder.Default
    @Column(name = "allow_half_day", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean allowHalfDay = true;

    @Builder.Default
    @Column(name = "allow_negative_balance", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean allowNegativeBalance = false;

    @Builder.Default
    @Column(name = "notice_period_restriction", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean noticePeriodRestriction = false;

    @OneToMany(mappedBy = "leaveType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LeaveRequest> leaveRequests;

    @OneToMany(mappedBy = "leaveType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LeaveBalance> leaveBalances;

    // Custom constructor for essential fields
    public LeaveType(String leaveName, String description) {
        this.leaveName = leaveName;
        this.description = description;
    }
}
