package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveRejectCompoffDTO {
    @NotNull(message = "required")
    private Long compoffId;
    @NotBlank(message= "required")
    private String managerId;
}
