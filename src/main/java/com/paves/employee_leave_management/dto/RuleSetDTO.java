package com.paves.employee_leave_management.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor // Useful for the mappers
public class RuleSetDTO {
    private UUID id;
    private String name;
    private String description;
    private Boolean active;
    private List<RuleConditionDTO> conditions; // Included for detail view, null for list view
    private List<ApprovalStepDTO> approvalSteps; // Included for detail view, null for list view
}
