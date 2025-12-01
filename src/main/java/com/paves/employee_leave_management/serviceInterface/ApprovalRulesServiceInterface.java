package com.paves.employee_leave_management.serviceInterface;


import com.paves.employee_leave_management.entities.ApprovalRule;

import java.util.List;

public interface ApprovalRulesServiceInterface {
    public void createApprovalRules(ApprovalRule approvalRules);
    public List<ApprovalRule> getAllApprovalRules();
    public void updateApprovalRules(ApprovalRule approvalRules);
    public void deleteApprovalRules(String id);
    public ApprovalRule getApprovalRules(String id);

}
