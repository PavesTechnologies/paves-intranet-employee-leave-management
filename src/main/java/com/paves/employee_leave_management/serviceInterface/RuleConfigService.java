package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApprovalStepDTO;
import com.paves.employee_leave_management.dto.RuleConditionDTO;
import com.paves.employee_leave_management.dto.RuleSetDTO;
import java.util.List;
import java.util.UUID;

public interface RuleConfigService {

    // --- RuleSet Methods ---
    List<RuleSetDTO> getAllRuleSets();
    RuleSetDTO getRuleSetById(UUID ruleSetId);
    RuleSetDTO createRuleSet(RuleSetDTO ruleSetDto);
    RuleSetDTO updateRuleSet(UUID ruleSetId, RuleSetDTO ruleSetDto);
    void deleteRuleSet(UUID ruleSetId);
    RuleSetDTO activateRuleSet(UUID ruleSetId); // Method to activate
    RuleSetDTO deactivateRuleSet(UUID ruleSetId); // Method to deactivate

    // --- RuleCondition Methods ---
    List<RuleConditionDTO> getConditionsForRuleSet(UUID ruleSetId);
    RuleConditionDTO addConditionToRuleSet(UUID ruleSetId, RuleConditionDTO conditionDto);
    RuleConditionDTO updateCondition(UUID conditionId, RuleConditionDTO conditionDto);
    void deleteCondition(UUID conditionId);

    // --- ApprovalStep Methods ---
    List<ApprovalStepDTO> getStepsForRuleSet(UUID ruleSetId);
    ApprovalStepDTO addStepToRuleSet(UUID ruleSetId, ApprovalStepDTO stepDto);
    ApprovalStepDTO updateStep(UUID stepId, ApprovalStepDTO stepDto);
    void deleteStep(UUID stepId);
}