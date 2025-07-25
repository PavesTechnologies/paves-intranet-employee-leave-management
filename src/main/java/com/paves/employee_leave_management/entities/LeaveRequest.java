package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

// LeaveRequest Entity
@Entity
@Table(name = "leave_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @Column(name = "leave_id")
    private String leaveId;

    @PrePersist
    public void generateId(){
        if(leaveId == null){
            leaveId = "LR"+ UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase();
        }
    }
    @ManyToOne

    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "days_requested", nullable = false)
    private Integer daysRequested;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

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

    // Custom constructor for essential fields
    public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate,
                        LocalDate endDate, Integer daysRequested, String reason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.daysRequested = daysRequested;
        this.reason = reason;
        this.status = LeaveStatus.PENDING;
        this.requestDate = LocalDate.now();
    }
}
