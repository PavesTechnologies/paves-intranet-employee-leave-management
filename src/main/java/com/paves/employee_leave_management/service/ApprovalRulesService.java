package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.ApprovalRule;
import com.paves.employee_leave_management.repo.ApprovalRuleRepo;
import com.paves.employee_leave_management.serviceInterface.ApprovalRulesServiceInterface;
import org.apache.poi.sl.draw.geom.GuideIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ApprovalRulesService implements ApprovalRulesServiceInterface {

    @Autowired
    private final ApprovalRuleRepo approvalRuleRepo;

    public ApprovalRulesService(ApprovalRuleRepo approvalRuleRepo) {
        this.approvalRuleRepo = approvalRuleRepo;
    }

    @Override
    public void createApprovalRules(ApprovalRule approvalRules) {
        approvalRuleRepo.findByActionType(approvalRules.getActionType())
                .ifPresent(rule -> {
                throw new RuntimeException("Approval rule already exists for action type: " + approvalRules.getActionType());
                });
        approvalRuleRepo.save(approvalRules);
    }
//    @Override
//    public void createApprovalRules(ApprovalRule approvalRules) {
//        approvalRuleRepo.findByActionType(approvalRules.getActionType())
//                .ifPresent(rule -> {
//                    if (rule.getApprovalLevel() == approvalRules.getApprovalLevel()) {
//                        throw new RuntimeException(
//                                "Approval rule already exists for action type: "
//                                        + approvalRules.getActionType()
//                                        + " and approval level: "
//                                        + approvalRules.getApprovalLevel());
//                    }
//                });
//
//        approvalRuleRepo.save(approvalRules);
//    }

    @Override
    public List<ApprovalRule> getAllApprovalRules() {
        List<ApprovalRule> approvalRules = approvalRuleRepo.findAll();
        if(approvalRules.isEmpty()){
            return null;
        }
        return approvalRules;
    }

    @Override
    public void updateApprovalRules(ApprovalRule approvalRules) {
        Optional<ApprovalRule> rule = approvalRuleRepo.findById(approvalRules.getId().toString());
        if(rule.isEmpty()){
            throw new RuntimeException("Approval rule not found for action type: " + approvalRules.getActionType());
        }
        approvalRuleRepo.save(approvalRules);
    }

    @Override
    public void deleteApprovalRules(String id) {
        Optional<ApprovalRule> rule = approvalRuleRepo.findById(id);
        if(rule.isEmpty()){
            throw new RuntimeException("Approval rule not found for id: " + id);
        }
        approvalRuleRepo.delete(rule.get());
    }

    @Override
    public ApprovalRule getApprovalRules(String id) {
        Optional<ApprovalRule> rule = approvalRuleRepo.findById(id);
        if(rule.isEmpty()){
            throw new RuntimeException("Approval rule not found for id: " + id);
        }
        return rule.get();
    }
}
