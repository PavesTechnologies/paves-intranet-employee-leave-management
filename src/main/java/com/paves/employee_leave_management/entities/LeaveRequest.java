package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.audit.AuditEntityListener;
import com.paves.employee_leave_management.enums.ApproverType;
import com.paves.employee_leave_management.enums.LeaveStatus;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// LeaveRequest Entity
@Entity
@Table(name = "leave_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LeaveRequest {

    @Id
    @Column(name = "leave_id")
    private String leaveId;

    @PrePersist
    public void generateId(){
        if(leaveId == null){
            leaveId = "LR"+ UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase();
        }

        if(year == null) {
            year = requestDate.getYear();
        }
    }

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "emp_id")
    private String employeeId;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "days_requested", nullable = false)
    private double daysRequested;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "drive_link", columnDefinition = "TEXT")
    private String driveLink;

    @Column(name = "start_session",columnDefinition = "TEXT")
    private String startSession;

    @Column(name = "end_session",columnDefinition = "TEXT")
    private String endSession;

    @Column(name = "manager_comment", columnDefinition = "TEXT")
    private String managerComment;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    private LeaveStatus status = LeaveStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    @JsonIgnoreProperties({"firstName","lastName","email","gender","phone","hireDate","salary","jobTitle","password"})
    private Employee approvedBy;

    @Builder.Default
    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate = LocalDate.now();

    @Column(name = "response_date")
    private LocalDate responseDate;

    @Column(name = "leave_name")
    private LocalDate leaveName;

    @Column(name = "year")
    private Integer year;


    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "last_updated_at", insertable = false)
    private LocalDateTime lastUpdatedAt;

    // Custom constructor for essential fields
    public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate,
                        LocalDate endDate, Integer daysRequested, String reason, String driveLink) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.daysRequested = daysRequested;
        this.reason = reason;
        this.driveLink = driveLink;
        this.status = LeaveStatus.PENDING;
        this.requestDate = LocalDate.now();
    }

    public LeaveTypeRepo getLeaveName() {
        return null;
    }
}
