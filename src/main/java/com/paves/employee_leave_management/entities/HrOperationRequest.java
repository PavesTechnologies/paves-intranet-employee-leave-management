package com.paves.employee_leave_management.entities; // Or a new sub-package like .hr

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the data payload for an HR Operation that requires maker-checker approval.
 * The generic 'Request' entity will point to this table's ID.
 */
@Entity
@Table(name = "hr_operation_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HrOperationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // The primary key for this data payload

    /**
     * The ID of the generic workflow Request that is managing the approval for this object.
     * This provides a two-way link.
     */
    @Column(name = "workflow_request_id")
    private UUID workflowRequestId;

    /**
     * The type of operation, copied here for clarity.
     * e.g., "ADD_LEAVE_TYPE", "UPDATE_EMPLOYEE_BALANCE", "DEACTIVATE_LEAVE_TYPE"
     */
    @Column(name = "operation_type", nullable = false)
    private String operationType;

    /**
     * The Employee ID (String) of the "maker" who created this request.
     */
    @Column(name = "created_by_employee_id", nullable = false)
    private String createdByEmployeeId;

    /**
     * The data for this operation, stored as a JSON blob.
     * For ADD_LEAVE_TYPE: {"leaveName": "Paternity", "maxDays": 10, ...}
     * For UPDATE_BALANCE: {"targetEmployeeId": "PAVEMP123", "adjustmentDays": 5, ...}
     */
    @Lob
    @Column(nullable = false)
    private String payload;

    /**
     * The status of this data object itself.
     * e.g., "PENDING_APPROVAL", "COMPLETED", "REJECTED"
     */
    @Column(nullable = false)
    private String status;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}