package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.paves.employee_leave_management.audit.AuditEntityListener;
import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@EntityListeners(AuditEntityListener.class)
public class GenderBasedLeave {

    @Id
    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @Column(unique = true, nullable = false, name = "leave_name")
    private String leaveName;

    @PrePersist
    public void generateId() {
        if(leaveTypeId==null){
            switch (LeaveTypesEnum.valueOf(leaveName)) {
                case MATERNITY_LEAVE -> leaveTypeId = "L-ML";
                case PATERNITY_LEAVE -> leaveTypeId = "L-PL";
            }
        }
    }

    @Column(name = "max_leave_days")
    private Integer maxLeaveDays;

    @Column(name = "min_leave_days")
    private Integer minLeaveDays;

    @Column(name = "waiting_period_days")
    private Integer waitingPeriodDays;

    @Column(name = "requires_documentation")
    private Boolean requiresDocumentation;

    @Column(name = "allow_negative_balance")
    private Boolean allowNegativeBalance;

    @Column(name = "gender")
    private String gender;

    @Column(name= "advance_notice")
    private Integer advanceNotice;

    @Column(name = "cool_down_period")
    private Integer coolDownPeriod;

    @Column(name = "notice_period_restrictions")
    private Boolean noticePeriodRestrictions;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "effective_start_date")
    private LocalDate effectiveStartDate;

    @OneToMany(mappedBy = "leaveType", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<GenderBasedLeaveBalance> leaveBalances;


    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Builder.Default
    @Column(name = "weekends_and_holidays_allowed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean weekendsAndHolidaysAllowed = false;
    
}
