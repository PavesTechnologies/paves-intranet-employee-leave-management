package com.paves.employee_leave_management.service.ruleengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.RuleCondition;
import com.paves.employee_leave_management.entities.RuleSet;
import com.paves.employee_leave_management.repo.RuleConditionRepository;
import com.paves.employee_leave_management.repo.RuleSetRepository;
import com.paves.employee_leave_management.service.ruleengine.helper.ConditionEvaluationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RuleEvaluatorService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleConditionRepository ruleConditionRepository;
    private final ConditionEvaluationHelper evaluationHelper;
    private final ObjectMapper objectMapper;

    /**
     * Evaluates a request against all active rulesets and returns the first one that matches.
     */
    public Optional<RuleSet> evaluate(Request request) {
        List<RuleSet> activeRuleSets = getActiveRules(); // Uses cache

        Map<String, Object> attributes = parseMakerAttributes(request);

        for (RuleSet ruleSet : activeRuleSets) {
            List<RuleCondition> conditions = ruleConditionRepository.findByRuleSetId(ruleSet.getId());
            if (conditions.isEmpty()) {
                continue; // Skip rulesets with no conditions
            }

            try {
                if (allConditionsMatch(request, attributes, conditions)) {
                    log.info("Request {} matched RuleSet {}", request.getId(), ruleSet.getName());
                    return Optional.of(ruleSet);
                }
            } catch (Exception e) {
                log.error("Failed to evaluate RuleSet {} for Request {}: {}",
                        ruleSet.getId(), request.getId(), e.getMessage());
            }
        }

        log.warn("Request {} did not match any active RuleSet.", request.getId());
        return Optional.empty(); // No match found, or a fallback rule could be returned here
    }

    /**
     * Caches the active rules to avoid frequent DB hits on every request.
     * The cache "activeRuleSets" should be evicted whenever a RuleSet is created/updated.
     */
    @Cacheable("activeRuleSets")
    public List<RuleSet> getActiveRules() {
        log.debug("Fetching and caching active rulesets from DB.");
        return ruleSetRepository.findByActiveTrue();
    }

    /**
     * Evicts the ruleset cache. Call this after any C/U/D operation on RuleSets.
     */
    @CacheEvict(value = "activeRuleSets", allEntries = true)
    public void clearRuleCache() {
        log.info("Active ruleset cache evicted.");
    }

    private boolean allConditionsMatch(Request request, Map<String, Object> attributes, List<RuleCondition> conditions) {
        for (RuleCondition condition : conditions) {
            Object actualValue = resolveAttribute(request, attributes, condition.getAttribute());
            if (actualValue == null) {
                log.warn("Could not resolve attribute '{}' for request {}. Condition fails.",
                        condition.getAttribute(), request.getId());
                return false;
            }

            if (!evaluationHelper.evaluate(actualValue, condition.getOperator(), condition.getValue())) {
                return false; // One condition failed, so the ruleset doesn't match
            }
        }
        return true; // All conditions passed
    }

    /**
     * Resolves the value of an attribute from the Request object or its JSON makerAttributes.
     */
    private Object resolveAttribute(Request request, Map<String, Object> attributes, String attributeName) {
        // 1. Check direct fields on the Request object
        if ("totalDays".equals(attributeName)) return request.getTotalDays();
        if ("leaveType".equals(attributeName)) return request.getLeaveType();
        if ("requestType".equals(attributeName)) return request.getRequestType();
        if ("operationType".equals(attributeName)) return request.getOperationType();
        if ("targetGroupId".equals(attributeName)) return request.getTargetGroupId();

        // 2. Check fields from the makerAttributes JSON blob (e.g., "maker.role")
        if (attributeName.startsWith("maker.")) {
            return attributes.get(attributeName.substring(6)); // Get value for "role", "department", etc.
        }

        return attributes.get(attributeName); // Fallback to root of JSON
    }

    private Map<String, Object> parseMakerAttributes(Request request) {
        try {
            if (request.getMakerAttributes() == null || request.getMakerAttributes().isBlank()) {
                return Map.of();
            }
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            return objectMapper.readValue(request.getMakerAttributes(), typeRef);
        } catch (Exception e) {
            log.error("Failed to parse makerAttributes JSON for Request {}: {}", request.getId(), e.getMessage());
            return Map.of(); // Return empty map on failure
        }
    }
}