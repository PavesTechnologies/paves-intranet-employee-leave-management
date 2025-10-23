package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.RuleCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleConditionRepository extends JpaRepository<RuleCondition, UUID> {

    List<RuleCondition> findByRuleSetId(UUID ruleSetId);
}
