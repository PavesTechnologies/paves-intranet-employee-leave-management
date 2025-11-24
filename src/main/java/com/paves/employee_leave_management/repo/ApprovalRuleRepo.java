package com.paves.employee_leave_management.repo;


import com.paves.employee_leave_management.entities.ApprovalRule;
import com.paves.employee_leave_management.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApprovalRuleRepo extends JpaRepository<ApprovalRule, String> {
    Optional<ApprovalRule> findByActionType(ActionType type);
}
