package com.paves.employee_leave_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelCompoffRequestDTO {
    @NotBlank(message = "required")
    private Long compoffId;
    @NotBlank(message = "required")
    private String reason;
}
