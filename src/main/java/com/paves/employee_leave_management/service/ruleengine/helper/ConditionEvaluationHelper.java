package com.paves.employee_leave_management.service.ruleengine.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionEvaluationHelper {

    // Using ObjectMapper for safe type conversions, e.g., from JSON numbers
    private final ObjectMapper objectMapper;

    public boolean evaluate(Object actualValue, String operator, String expectedValue) {
        try {
            switch (operator.toUpperCase()) {
                case "EQUALS":
                case "==":
                    return Objects.equals(String.valueOf(actualValue), expectedValue);

                case "NOT_EQUALS":
                case "!=":
                    return !Objects.equals(String.valueOf(actualValue), expectedValue);

                case "GREATER_THAN":
                case ">":
                    return convertToNumber(actualValue) > Double.parseDouble(expectedValue);

                case "LESS_THAN":
                case "<":
                    return convertToNumber(actualValue) < Double.parseDouble(expectedValue);

                case "GREATER_THAN_OR_EQUALS":
                case ">=":
                    return convertToNumber(actualValue) >= Double.parseDouble(expectedValue);

                case "LESS_THAN_OR_EQUALS":
                case "<=":
                    return convertToNumber(actualValue) <= Double.parseDouble(expectedValue);

                case "IN":
                    // Expected value is a comma-separated list, e.g., "SICK,CASUAL"
                    List<String> expectedList = Arrays.asList(expectedValue.split(","));
                    return expectedList.contains(String.valueOf(actualValue));

                case "NOT_IN":
                    List<String> notInList = Arrays.asList(expectedValue.split(","));
                    return !notInList.contains(String.valueOf(actualValue));

                default:
                    log.warn("Unsupported operator: {}", operator);
                    return false;
            }
        } catch (Exception e) {
            log.error("Failed to evaluate condition: {} {} {}. Error: {}",
                    actualValue, operator, expectedValue, e.getMessage());
            return false;
        }
    }

    private double convertToNumber(Object value) {
        // Safely convert from various numeric types (Integer, Double, Long)
        return objectMapper.convertValue(value, Double.class);
    }
}