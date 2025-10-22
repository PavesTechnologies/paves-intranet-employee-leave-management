package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ApprovalStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    private int level;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    private String approverId;

}
