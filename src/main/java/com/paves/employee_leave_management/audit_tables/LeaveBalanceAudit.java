package com.paves.employee_leave_management.audit_tables;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balance_audit")
@Data
public class LeaveBalanceAudit extends BaseAuditEntity {

    private String balanceId;

    private String employeeId;

    private String leaveTypeId;

    private double totalLeaves;

    private double accruedLeaves;

    private double usedLeaves;

    private double remainingLeaves;

    private double carriedForward;

    private double expiredLeaves;

    private Integer encashedLeaves;

    private Integer year;

    private LocalDate lastAccrualDate;

    private LocalDate createdAt;

    private LocalDate lastUpdatedAt;

    // The BaseAuditEntity already has:
    // private String action;
    // private String changedBy;
    // private LocalDateTime changedAt;
}
