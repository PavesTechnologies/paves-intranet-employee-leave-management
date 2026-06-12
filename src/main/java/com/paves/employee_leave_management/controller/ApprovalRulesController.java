package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.ApprovalRule;
import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.ApproverType;
import com.paves.employee_leave_management.serviceInterface.ApprovalRulesServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approval-rules")
public class ApprovalRulesController {

    @Autowired
    private final ApprovalRulesServiceInterface approvalRulesService;



    public ApprovalRulesController(ApprovalRulesServiceInterface approvalRulesService) {
        this.approvalRulesService = approvalRulesService;
    }


    @GetMapping("/action-types")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public List<ActionType> getActionTypes() {
        return List.of(ActionType.values());
    }

    @GetMapping("/approver-types")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public List<ApproverType> getApproverTypes() {
        return List.of(ApproverType.values());
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public void createApprovalRules(@RequestBody ApprovalRule approvalRules) {
        approvalRulesService.createApprovalRules(approvalRules);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ApiResponse<List<ApprovalRule>> getAllApprovalRules() {
        List<ApprovalRule> approvalRules = approvalRulesService.getAllApprovalRules();
        return new ApiResponse<>(true, "Approval rules retrieved successfully", approvalRules);
    }

    @GetMapping("/get/{id}")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public ApiResponse<ApprovalRule> getApprovalRules(@PathVariable String id) {
        ApprovalRule approvalRules = approvalRulesService.getApprovalRules(id);
        return new ApiResponse<>(true, "Approval rules retrieved successfully", approvalRules);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public void updateApprovalRules(@RequestBody ApprovalRule approvalRules) {
        approvalRulesService.updateApprovalRules(approvalRules);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('HR', 'SUPER_ADMIN')")
    public void deleteApprovalRules(@PathVariable String id) {
        approvalRulesService.deleteApprovalRules(id);
    }
}
