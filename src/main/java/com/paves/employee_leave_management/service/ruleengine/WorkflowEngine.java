package com.paves.employee_leave_management.service.ruleengine;

import com.paves.employee_leave_management.entities.ApprovalAction;
import com.paves.employee_leave_management.entities.ApprovalStage;
import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.ApprovalStep;
import com.paves.employee_leave_management.entities.RuleSet;
import com.paves.employee_leave_management.enums.ApprovalMode;
import com.paves.employee_leave_management.repo.ApprovalActionRepository;
import com.paves.employee_leave_management.repo.ApprovalStageRepository;
import com.paves.employee_leave_management.repo.RequestRepository;
import com.paves.employee_leave_management.repo.ApprovalStepRepository;
import com.paves.employee_leave_management.service.ruleengine.resolver.ApproverResolverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkflowEngine {

    private final RequestRepository requestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalStageRepository approvalStageRepository;
    private final ApprovalActionRepository approvalActionRepository;

    private final ApproverResolverFactory resolverFactory;
    private final DelegationService delegationService;
    // private final NotificationService notificationService;

    public void startWorkflow(Request request, RuleSet ruleSet) {
        log.info("Starting workflow for Request {} using RuleSet {}", request.getId(), ruleSet.getName());
        List<ApprovalStep> steps = approvalStepRepository.findByRuleSetIdOrderByLevelAsc(ruleSet.getId());

        if (steps.isEmpty()) {
            log.warn("RuleSet {} has no steps. Auto-approving Request {}.", ruleSet.getName(), request.getId());
            request.setStatus("APPROVED");
            requestRepository.save(request);
            return;
        }

        int minLevel = steps.get(0).getLevel();
        boolean firstLevelActivated = false;

        for (ApprovalStep step : steps) {
            List<String> approverIds = resolverFactory.getResolver(step.getApproverType()) // Changed
                    .resolve(request, step);

            for (String originalApproverId : approverIds) { // Changed
                String finalApproverId = delegationService.findActiveDelegate(originalApproverId); // Changed

                String initialStatus;
                if (finalApproverId.equals(request.getCreatedBy())) {
                    log.info("Skipping self-approval for Request {} on Level {}",
                            request.getId(), step.getLevel());
                    initialStatus = "SKIPPED";
                } else if (step.getLevel() == minLevel) {
                    initialStatus = "PENDING";
                    firstLevelActivated = true;
                } else {
                    initialStatus = "WAITING";
                }

                createStage(request, step, finalApproverId, initialStatus);
            }
        }

        if (!firstLevelActivated && !steps.isEmpty()) {
            log.info("First level was entirely skipped, checking for next level.");
            checkAndAdvanceWorkflow(request, minLevel);
        }
    }

    public void processAction(UUID stageId, String actionByEmployeeId, String actionType, String comment) { // Changed
        ApprovalStage stage = approvalStageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage not found: " + stageId));

        Request request = stage.getRequest();

        if (!"PENDING".equals(stage.getStatus())) {
            throw new IllegalStateException("Action cannot be taken. Stage " + stageId + " is not in PENDING state.");
        }

        String designatedApprover = stage.getApproverId();
        if (!designatedApprover.equals(actionByEmployeeId)) {
            String currentDelegate = delegationService.findActiveDelegate(designatedApprover);
            if (!currentDelegate.equals(actionByEmployeeId)) {
                throw new SecurityException("User " + actionByEmployeeId + " is not the designated approver for stage " + stageId);
            }
        }

        if (request.getStatus().equals("APPROVED") || request.getStatus().equals("REJECTED")) {
            throw new IllegalStateException("Request " + request.getId() + " is already finalized.");
        }

        ApprovalAction action = ApprovalAction.builder()
                .stage(stage)
                .actionBy(actionByEmployeeId) // Changed
                .actionType(actionType.toUpperCase())
                .comment(comment)
                .actionAt(LocalDateTime.now())
                .build();
        approvalActionRepository.save(action);

        if ("REJECT".equals(actionType.toUpperCase())) {
            log.info("Request {} REJECTED at Level {}", request.getId(), stage.getLevel());
            stage.setStatus("REJECTED");
            stage.setActionAt(LocalDateTime.now());

            request.setStatus("REJECTED");
            requestRepository.save(request);

        } else if ("APPROVE".equals(actionType.toUpperCase())) {
            log.info("Stage {} for Request {} APPROVED", stage.getId(), request.getId());
            stage.setStatus("APPROVED");
            stage.setActionAt(LocalDateTime.now());

            checkAndAdvanceWorkflow(request, stage.getLevel());
        }
    }

    private void checkAndAdvanceWorkflow(Request request, int completedLevel) {
        List<ApprovalStage> allStages = approvalStageRepository.findByRequestIdOrderByLevelAsc(request.getId());

        List<ApprovalStage> currentLevelStages = allStages.stream()
                .filter(s -> s.getLevel() == completedLevel)
                .toList();

        boolean allApprovedOrSkipped = currentLevelStages.stream()
                .allMatch(s -> "APPROVED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus()));

        if (allApprovedOrSkipped) {
            log.info("Level {} complete for Request {}", completedLevel, request.getId());

            Integer nextLevel = allStages.stream()
                    .filter(s -> s.getLevel() > completedLevel)
                    .map(ApprovalStage::getLevel)
                    .min(Integer::compareTo)
                    .orElse(null);

            if (nextLevel == null) {
                log.info("Final approval level complete. Request {} APPROVED.", request.getId());
                request.setStatus("APPROVED");
                requestRepository.save(request);
            } else {
                log.info("Advancing Request {} to Level {}", request.getId(), nextLevel);
                List<ApprovalStage> nextLevelStages = allStages.stream()
                        .filter(s -> s.getLevel() == nextLevel && "WAITING".equals(s.getStatus()))
                        .toList();

                for (ApprovalStage stage : nextLevelStages) {
                    stage.setStatus("PENDING");
                }
                approvalStageRepository.saveAll(nextLevelStages);

                if (nextLevelStages.stream().allMatch(s -> "SKIPPED".equals(s.getStatus()))) {
                    checkAndAdvanceWorkflow(request, nextLevel);
                }
            }
        }
    }

    private ApprovalStage createStage(Request request, ApprovalStep step, String approverId, String status) { // Changed
        ApprovalStage stage = ApprovalStage.builder()
                .request(request)
                .level(step.getLevel())
                .approverId(approverId) // Changed
                .status(status)
                .assignedAt(LocalDateTime.now())
                .build();
        return approvalStageRepository.save(stage);
    }
}