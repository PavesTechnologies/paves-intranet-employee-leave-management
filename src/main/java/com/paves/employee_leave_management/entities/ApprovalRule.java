package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.ApproverType;

import javax.persistence.*;

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

    private String condition;

    @Enumerated(EnumType.STRING)
    private ApproverType approverType;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getMakerRole() {
        return makerRole;
    }

    public void setMakerRole(String makerRole) {
        this.makerRole = makerRole;
    }

    public String getCheckerRole() {
        return checkerRole;
    }

    public void setCheckerRole(String checkerRole) {
        this.checkerRole = checkerRole;
    }

    public int getApprovalLevel() {
        return approvalLevel;
    }

    public void setApprovalLevel(int approvalLevel) {
        this.approvalLevel = approvalLevel;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public ApproverType getApproverType() {
        return approverType;
    }

    public void setApproverType(ApproverType approverType) {
        this.approverType = approverType;
    }
}
