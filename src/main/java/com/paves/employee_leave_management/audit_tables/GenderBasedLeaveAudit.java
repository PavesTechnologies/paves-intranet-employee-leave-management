package com.paves.employee_leave_management.audit_tables;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GenderBasedLeaveAudit extends  BaseAuditEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // primary key for audit table

    private String leaveTypeId;
    private String leaveName;
    private Integer maxLeaveDays;
    private Integer minLeaveDays;
    private Integer waitingPeriodDays;
    private Boolean requiredDocumentation;
    private Boolean allowNegativeBalance;
    private String gender;
    private String advancedNotice;
    private Integer coolDownPeriod;
    private Boolean noticePeriodRestrictions;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private Boolean weekendsAndHolidaysAllowed;


}
