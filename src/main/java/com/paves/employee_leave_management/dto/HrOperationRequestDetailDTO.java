package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.HrOperationRequest; // Assuming this has payload
import com.paves.employee_leave_management.entities.ApprovalAction;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Data
@Builder
public class HrOperationRequestDetailDTO {
    private UUID stageId;
    private UUID requestId;
    private String requestType; // "HR_OPERATION"
    private String operationType;
    private String makerId;
    private String makerName;
    private LocalDateTime requestCreatedAt;
    private String currentWorkflowStatus; // e.g., PENDING
    private String hrOperationStatus; // e.g., PENDING_APPROVAL
    private String payload; // The full JSON payload
    // Approval History (Optional)
    private List<ApprovalAction> history;
}