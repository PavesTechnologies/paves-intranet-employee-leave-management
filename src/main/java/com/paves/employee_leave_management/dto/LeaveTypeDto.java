package com.paves.employee_leave_management.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveTypeDto {

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
}
