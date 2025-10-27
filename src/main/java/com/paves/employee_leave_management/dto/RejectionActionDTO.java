package com.paves.employee_leave_management.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RejectionActionDTO {
    @NotBlank(message = "Rejection reason/comment is required.")
    private String comment;
}