package com.paves.employee_leave_management.audit_tables;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.paves.employee_leave_management.entities.LeaveBalance;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "leave_type_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveTypeAudit extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // primary key for audit table

    private String leaveTypeId;
    private String leaveName;
    private String description;
    private Integer maxDaysPerYear;
    private Integer maxCarryForwardPerYear;
    private Integer maxCarryForward;
    private Boolean requiresDocumentation;
    private Double accrualRate;
    private String accrualFrequency;
    private Integer expiryDays;
    private Integer waitingPeriodDays;
    private Integer advanceNoticeDays;
    private Integer pastDateLimitDays;
    private Boolean allowHalfDay;
    private Boolean allowNegativeBalance;
    private Boolean noticePeriodRestriction;
    private Boolean weekendsAndHolidaysAllowed;
    private Boolean active;

//    @JsonIgnore
//    private Set<LeaveBalance> leaveBalances;




//    @Column(name = "changed_by")
//    private Long changedBy; // userId from JWT
//
//    @Column(name = "changed_at")
//    private LocalDate changedAt;
//
//    @Column(name = "action", length = 20)
//    private String action; // INSERT, UPDATE, DELETE
}
