package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalRule;
import com.paves.employee_leave_management.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {

    List<ApprovalRule> findByActionTypeAndMakerRole(ActionType actionType, String makerRole);
}
