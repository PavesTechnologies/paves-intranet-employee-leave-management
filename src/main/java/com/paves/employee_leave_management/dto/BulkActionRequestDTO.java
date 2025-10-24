package com.paves.employee_leave_management.dto;

import lombok.Data;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Data
public class BulkActionRequestDTO {
    @NotEmpty
    private List<UUID> stageIds; // IDs of the ApprovalStage entities
    @NotNull
    private String approverId;   // Employee ID of the user taking action
    @NotNull
    private String actionType;   // "APPROVE" or "REJECT"
    private String comment;      // Optional comment for rejections
}