package com.paves.employee_leave_management.service.event;

import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.event.WorkflowCompletionEvent;
import com.paves.employee_leave_management.service.HrOperationProcessorService; // You'll create this next
import com.paves.employee_leave_management.service.LeaveRequestProcessorService; // You'll create this next
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener; // Use this for simplicity
import org.springframework.scheduling.annotation.Async; // Optional: Run processor asynchronously
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowCompletionListener {

    private final HrOperationProcessorService hrOperationProcessorService;
    private final LeaveRequestProcessorService leaveRequestProcessorService;

    /**
     * Listens for completed workflow events *after* the transaction commits.
     * Routes the event to the appropriate processor based on requestType.
     * @param event The event containing the completed Request object.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async // Optional: Process the final logic asynchronously
    public void handleWorkflowCompletion(WorkflowCompletionEvent event) {
        Request request = event.getRequest();
        log.info("Workflow {} completed with status: {}. Triggering final processing.",
                request.getId(), request.getStatus());

        try {
            switch (request.getRequestType()) {
                case "HR_OPERATION":
                    hrOperationProcessorService.process(request);
                    break;
                case "LEAVE":
                    if ("APPROVED".equals(request.getStatus())) {
                        leaveRequestProcessorService.processApproved(request);
                    } else if ("REJECTED".equals(request.getStatus()) || "CANCELLED".equals(request.getStatus())) {
                        leaveRequestProcessorService.processRejectedOrCancelled(request);
                    }
                    break;
                default:
                    log.warn("No processor defined for completed requestType: {}", request.getRequestType());
            }
        } catch (Exception e) {
            log.error("Error processing completed workflow {} (Request ID: {}): {}",
                    request.getId(), request.getTargetEntityId(), e.getMessage(), e);
            // Consider adding retry logic or marking the request/payload for manual review
        }
    }
}