package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.paves.employee_leave_management.audit.AuditEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "leave_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditEntityListener.class)
public class LeaveType {

    @Id
    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @PrePersist
    public void generateId() {
        if ((leaveTypeId == null || leaveTypeId.isBlank()) && leaveName != null) {
            switch (LeaveTypesEnum.valueOf(leaveName)) {
                case MATERNITY_LEAVE -> leaveTypeId = "L-ML";
                case PATERNITY_LEAVE -> leaveTypeId = "L-PL";
                case SICK_LEAVE -> leaveTypeId = "L-SL";
                case EARNED_LEAVE -> leaveTypeId = "L-EL";
                case UNPAID_LEAVE -> leaveTypeId = "L-UP";
                case COMPENSATORY_LEAVE -> leaveTypeId = "L-COMPOFF";
            }
        }
    }

    @Column(name = "leave_name", length = 50, nullable = false)
    private String leaveName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_days_per_year")
    private Integer maxDaysPerYear;

    @Column(name = "max_carry_forward_per_year", columnDefinition = "INT DEFAULT 0")
    private Integer maxCarryForwardPerYear = 0;

    @Column(name = "max_carry_forward", columnDefinition = "INT DEFAULT 0")
    private Integer maxCarryForward = 0;

    @Column(name = "requires_documentation", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean requiresDocumentation = false;

    @Column(name = "accrual_rate", nullable = true, columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double accrualRate = 0.0;

    @Column(name = "accrual_frequency", length = 20)
    private String accrualFrequency;

    @Column(name = "expiry_days", columnDefinition = "INT DEFAULT 0")
    private Integer expiryDays;

    @Column(name = "waiting_period_days", columnDefinition = "INT DEFAULT 0")
    private Integer waitingPeriodDays = 0;

    @Column(name = "advance_notice_days", columnDefinition = "INT DEFAULT 0")
    private Integer advanceNoticeDays = 0;

    @Column(name = "past_date_limit_days", columnDefinition = "INT DEFAULT 0")
    private Integer pastDateLimitDays = 0;

    @Column(name = "allow_half_day", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean allowHalfDay = true;

    @Column(name = "allow_negative_balance", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean allowNegativeBalance = false;

    @Column(name = "notice_period_restriction", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean noticePeriodRestriction = false;

    @Column(name = "weekends_and_holidays_allowed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean weekendsAndHolidaysAllowed = false;

    @JsonIgnore // prevents infinite recursion
    @OneToMany(mappedBy = "leaveType", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private List<LeaveBalance> leaveBalances;

    @Column(name = "active")
    private Boolean active = true;

//    @CreatedDate
//    @Column(name = "created_at", updatable = false)
//    private LocalDate createdAt;
//
//    @LastModifiedDate
//    @Column(name = "last_updated_at", insertable = false)
//    private LocalDate lastUpdatedAt;

    @Transient
    private LeaveType snapShot;

    @PostLoad
    public void storeSnapShot() {
        this.snapShot = new LeaveType();
        BeanUtils.copyProperties(this, snapShot);
    }

    public LeaveType getSnapShot() {
        return snapShot;
    }
}
