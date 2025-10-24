package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class BulkActionResultDTO {
    private List<UUID> successfulStageIds;
    private List<FailedAction> failedActions;

    @Getter
    @AllArgsConstructor
    public static class FailedAction {
        private UUID stageId;
        private String reason;
    }
}