package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.ChangeImpact;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproverUpdateResponseDTO {
    private boolean success;
    private String leaveId;
    private String workflowRequestId;
    private ChangeImpact impactLevel;
    private List<String> changesSummary;
    private String actionTaken; // "WORKFLOW_RESTARTED", "WORKFLOW_PRESERVED", "VALIDATION_FAILED"
    private List<String> errors;
    private String message;

    // For MINOR changes where choice is needed
    private boolean requiresApproverDecision;
    private String decisionPrompt;
}
