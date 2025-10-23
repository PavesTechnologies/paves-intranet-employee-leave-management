package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    List<ApprovalStep> findByRuleSetIdOrderByLevelAsc(UUID ruleSetId);
}
