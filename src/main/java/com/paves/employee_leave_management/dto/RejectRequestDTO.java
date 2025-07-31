package com.paves.employee_leave_management.dto;

import lombok.Data;

@Data
public class RejectRequestDTO {
    private String managerId;
    private String comment;
}
