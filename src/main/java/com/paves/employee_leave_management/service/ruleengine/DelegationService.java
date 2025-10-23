package com.paves.employee_leave_management.service.ruleengine;

import com.paves.employee_leave_management.entities.DelegationRule;
import com.paves.employee_leave_management.repo.DelegationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelegationService {

    private final DelegationRuleRepository delegationRuleRepository;

    /**
     * Checks if an original approver has an active delegation and returns the delegate's ID.
     * If no active delegation exists, it returns the original approver's ID.
     *
     * @param ownerUserId The Employee ID (String) of the original approver.
     * @return The Employee ID (String) of the delegate, or the original ID.
     */
    public String findActiveDelegate(String ownerUserId) { // Changed
        LocalDateTime now = LocalDateTime.now();

        return delegationRuleRepository
                .findByOwnerUserIdAndValidFromBeforeAndValidToAfter(ownerUserId, now, now) // Changed
                .map(DelegationRule::getDelegateUserId)
                .orElse(ownerUserId);
    }
}