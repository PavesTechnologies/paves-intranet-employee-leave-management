package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManagerPendingCompoffDTO {
    @NotBlank(message = "required")
    private String managerId;
}
