package com.paves.employee_leave_management.audit_tables;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "leave_request_audit")
@Data
public class LeaveRequestAudit extends BaseAuditEntity {

    private String leaveId;

    private String employeeId;

    private String leaveTypeId;

    private LocalDate startDate;

    private LocalDate endDate;

    private double daysRequested;

    private String reason;

    private String driveLink;

    private String startSession;

    private String endSession;

    private String managerComment;

    private String status;

    private String approvedById;

    private LocalDate requestDate;

    private LocalDate responseDate;

    private LocalDate leaveName;

    private Integer year;

    private LocalDate createdAt;

    private LocalDate lastUpdatedAt;

    // BaseAuditEntity already has:
    // private String action;
    // private String changedBy;
    // private LocalDateTime changedAt;
}