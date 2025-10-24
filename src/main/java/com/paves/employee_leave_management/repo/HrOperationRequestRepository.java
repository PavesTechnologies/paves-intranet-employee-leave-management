package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.HrOperationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing HrOperationRequest entities.
 * These entities store the data payload for HR operations awaiting approval.
 */
@Repository
public interface HrOperationRequestRepository extends JpaRepository<HrOperationRequest, UUID> {

    /**
     * Finds an HR operation request by the ID of the workflow Request that manages its approval.
     * Useful for linking back from the workflow to the specific data payload.
     *
     * @param workflowRequestId The UUID of the generic workflow Request.
     * @return An Optional containing the HrOperationRequest if found.
     */
    Optional<HrOperationRequest> findByWorkflowRequestId(UUID workflowRequestId);

    // Add any other specific query methods you might need, for example:
    // List<HrOperationRequest> findByCreatedByEmployeeIdAndStatus(String employeeId, String status);
    // List<HrOperationRequest> findByOperationTypeAndStatus(String operationType, String status);

}