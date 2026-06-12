package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.ApproverType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "approval_rules")
public class ApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    @Column(nullable = false)
    private String makerRole;

    @Column(nullable = false)
    private String checkerRole;

    private int approvalLevel;

    @Column(name = "approval_condition") // Renamed to avoid SQL reserved keyword conflict
    private String approvalCondition;

    @Enumerated(EnumType.STRING)
    private ApproverType approverType;

    // Getters and Setters

}
