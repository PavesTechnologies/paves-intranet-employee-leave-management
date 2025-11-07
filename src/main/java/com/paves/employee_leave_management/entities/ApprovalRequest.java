package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String workflowId;

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private ApprovalRule rule;

    @Column(nullable = false)
    private Long makerId;

    private Long approverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Lob
    private String payload; // Using String to store serialized JSON

    private String rejectionReason;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    // Getters and Setters

}
