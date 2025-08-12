package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "leave_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType {

    @Id
    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @PrePersist
    public void generateId() {
        if (leaveTypeId == "" && leaveName != null) {
            if (leaveName.equals(LeaveTypesEnum.MATERNITY_LEAVE.toString())) {
                leaveTypeId = "L-ML";
            } else if (leaveName.equals(LeaveTypesEnum.PATERNITY_LEAVE.toString())) {
                leaveTypeId = "L-PL";
            } else if (leaveName.equals(LeaveTypesEnum.SICK_LEAVE.toString())) {
                leaveTypeId = "L-SL";
            } else if (leaveName.equals(LeaveTypesEnum.EARNED_LEAVE.toString())) {
                leaveTypeId = "L-EL";
            } else if (leaveName.equals(LeaveTypesEnum.UNPAID_LEAVE.toString())) {
                leaveTypeId = "L-UP";
            } else if (leaveName.equals(LeaveTypesEnum.COMPENSATORY_LEAVE.toString())) {
                leaveTypeId = "L-COMPOFF";
            }
        }
    }

    @Column(name = "leave_name", length = 50, nullable = false)
    private String leaveName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_days_per_year")
    private Integer maxDaysPerYear;

    @Builder.Default
    @Column(name = "max_carry_forward_per_year", columnDefinition = "INT DEFAULT 0")
    private Integer maxCarryForwardPerYear = 0;

    @Builder.Default
    @Column(name = "max_carry_forward", columnDefinition = "INT DEFAULT 0")
    private Integer maxCarryForward = 0;

    @Builder.Default
    @Column(name = "requires_documentation", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean requiresDocumentation = false;

    @Column(name = "accrual_rate")
    private Double accrualRate;

    @Column(name = "accrual_frequency", length = 20)
    private String accrualFrequency;

    @Column(name = "expiry_days",columnDefinition = "INT DEFAULT 0")
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

    @Builder.Default
    @Column(name = "weekends_and_holidays_allowed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean weekendsAndHolidaysAllowed = false;

    @JsonIgnore
    @OneToMany(mappedBy = "leaveType", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private List<LeaveBalance> leaveBalances;


    // Custom constructor with new field
    public LeaveType(String leaveName, String description, Boolean weekendsAndHolidaysAllowed) {
        this.leaveName = leaveName;
        this.description = description;
        this.weekendsAndHolidaysAllowed = weekendsAndHolidaysAllowed;
    }
}
