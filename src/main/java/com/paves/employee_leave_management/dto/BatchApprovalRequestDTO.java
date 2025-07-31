package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for batch leave approval with just leave IDs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApprovalRequestDTO {

    @NotBlank(message = "Manager ID is required")
    private String managerId;

    @NotEmpty(message = "At least one leave ID must be provided")
    private List<@NotBlank(message = "Leave ID cannot be blank") String> leaveIds;
}
