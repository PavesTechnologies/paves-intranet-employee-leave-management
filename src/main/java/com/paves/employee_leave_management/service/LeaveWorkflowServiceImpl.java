package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.ApprovalStage;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveApprovalRule;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.enums.ApprovalStatus;
import com.paves.employee_leave_management.enums.HierarchyMapping;
import com.paves.employee_leave_management.entities.LeaveStatus;
import com.paves.employee_leave_management.repo.ApprovalStageRepo;
import com.paves.employee_leave_management.repo.LeaveApprovalRuleRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveWorkflowServiceImpl implements LeaveWorkflowService {

    private final LeaveApprovalRuleRepo leaveApprovalRuleRepo;
    private final ApprovalStageRepo approvalStageRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final RestTemplate restTemplate; // Make sure to configure this as a Bean

    @Override
    @Transactional
    public void startLeaveWorkflow(LeaveRequest leaveRequest) {
        triggerNextLevel(leaveRequest, 0);
    }

    @Override
    @Transactional
    public void processApprovalAction(String leaveId, String approverId, String decision) {
        ApprovalStage currentStage = approvalStageRepo.findByLeaveRequest_LeaveIdAndApproverIdAndStatus(leaveId, approverId, ApprovalStatus.PENDING)
                .orElseThrow(() -> new EntityNotFoundException("Pending approval stage not found for leaveId: " + leaveId + " and approverId: " + approverId));

        LeaveRequest leaveRequest = currentStage.getLeaveRequest();

        if ("REJECT".equalsIgnoreCase(decision)) {
            currentStage.setStatus(ApprovalStatus.REJECTED);
            leaveRequest.setStatus(LeaveStatus.REJECTED);
            approvalStageRepo.save(currentStage);
            leaveRequestRepo.save(leaveRequest);
            // Optional: Add email notification logic for rejection
            return;
        }

        currentStage.setStatus(ApprovalStatus.APPROVED);
        approvalStageRepo.save(currentStage);

        // Check if all approvers at the current level have approved
        long pendingApprovalsAtLevel = approvalStageRepo.countByLeaveRequest_LeaveIdAndLevelAndStatus(leaveId, currentStage.getLevel(), ApprovalStatus.PENDING);

        if (pendingApprovalsAtLevel == 0) {
            triggerNextLevel(leaveRequest, currentStage.getLevel());
        }
    }

    private void triggerNextLevel(LeaveRequest leaveRequest, int currentLevel) {
        int nextLevel = currentLevel + 1;
        List<LeaveApprovalRule> rules = leaveApprovalRuleRepo.findByLevel(nextLevel);

        if (rules.isEmpty()) {
            // No more levels, the leave is fully approved
            leaveRequest.setStatus(LeaveStatus.APPROVED);
            leaveRequestRepo.save(leaveRequest);
            // Optional: Add email notification logic for final approval
            return;
        }

        for (LeaveApprovalRule rule : rules) {
            List<String> approverIds = findApprovers(rule, leaveRequest.getEmployee());
            for (String approverId : approverIds) {
                ApprovalStage nextStage = new ApprovalStage();
                nextStage.setLeaveRequest(leaveRequest);
                nextStage.setLevel(nextLevel);
                nextStage.setApproverId(approverId);
                nextStage.setStatus(ApprovalStatus.PENDING);
                approvalStageRepo.save(nextStage);
                // Optional: Add email notification logic for next level approvers
            }
        }
    }

    private List<String> findApprovers(LeaveApprovalRule rule, Employee employee) {
        if (rule.getHierarchyMapping() == HierarchyMapping.EMPLOYEE_MANAGER) {
            if (employee.getManager() != null) {
                return Collections.singletonList(employee.getManager().getEmployeeId());
            }
        } else if (rule.getHierarchyMapping() == HierarchyMapping.PROJECT_MAPPING) {
            String apiUrl = "http://192.168.2.37:4000/api/projects/member/" + employee.getEmployeeId();
            try {
                // Call API and get response as a List of Map (since JSON is an array of objects)
                List<Map<String, Object>> projectList = restTemplate.getForObject(apiUrl, List.class);

                if (projectList == null || projectList.isEmpty()) {
                    return Collections.emptyList();
                }

                // Extract ownerId (managerId) from each project
                Set<String> managerIds = projectList.stream()
                        .map(project -> project.get("ownerId"))
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toSet()); // distinct owners

                return new ArrayList<>(managerIds);

            } catch (Exception e) {
                e.printStackTrace(); // log properly in production
                return Collections.emptyList();
            }

        } else if (rule.getHierarchyMapping() == HierarchyMapping.FIXED_ROLE) {
            return Collections.singletonList(rule.getFixedApproverId());
        }
        return Collections.emptyList();
    }

}
