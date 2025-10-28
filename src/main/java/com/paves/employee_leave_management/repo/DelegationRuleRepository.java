package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.DelegationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DelegationRuleRepository extends JpaRepository<DelegationRule, UUID> {

    Optional<DelegationRule> findByOwnerUserIdAndValidFromBeforeAndValidToAfter(
            String ownerUserId, LocalDateTime now1, LocalDateTime now2
    );

    List<DelegationRule> findByDelegateUserId(String delegateUserId);
}
