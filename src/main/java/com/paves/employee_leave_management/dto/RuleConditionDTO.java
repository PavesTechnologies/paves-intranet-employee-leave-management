package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor // Useful for the mappers
public class RuleConditionDTO {
    private UUID id;
    private String attribute; // e.g., "leaveType", "totalDays", "maker.role"
    private String operator;  // e.g., "==", ">", "IN"
    private String value;     // e.g., "SICK", "3", "JUNIOR_DEV"
}